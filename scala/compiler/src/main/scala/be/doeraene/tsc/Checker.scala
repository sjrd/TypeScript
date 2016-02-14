package be.doeraene.tsc

/// <reference path="binder.ts"/>

/* @internal */
object Checker {
  var nextSymbolId = 1
  var nextNodeId = 1
  var nextMergeId = 1

  def getNodeId(node: Node): Int {
    if (!node.id) {
      node.id = nextNodeId
      nextNodeId++
    }
    return node.id
  }

  var checkTime = 0

  def getSymbolId(symbol: Symbol): Int {
    if (!symbol.id) {
      symbol.id = nextSymbolId
      nextSymbolId++
    }

    return symbol.id
  }

  def createTypeChecker(host: TypeCheckerHost, produceDiagnostics: Boolean): TypeChecker {
    // Cancellation that controls whether or not we can cancel in the middle of type checking.
    // In general cancelling is *not* safe for the type checker.  We might be in the middle of
    // computing something, and we will leave our internals in an inconsistent state.  Callers
    // who set the cancellation token should catch if a cancellation exception occurs, and
    // should throw away and create a new TypeChecker.
    //
    // Currently we only support setting the cancellation token when getting diagnostics.  This
    // is because diagnostics can be quite expensive, and we want to allow hosts to bail out if
    // they no longer need the information (for example, if the user started editing again).
    var cancellationToken: CancellationToken

    val Symbol = objectAllocator.getSymbolConstructor()
    val Type = objectAllocator.getTypeConstructor()
    val Signature = objectAllocator.getSignatureConstructor()

    var typeCount = 0
    var symbolCount = 0

    val emptyArray: any[] = []
    val emptySymbols: SymbolTable = {}

    val compilerOptions = host.getCompilerOptions()
    val languageVersion = compilerOptions.target || ScriptTarget.ES3
    val modulekind = getEmitModuleKind(compilerOptions)
    val allowSyntheticDefaultImports = typeof compilerOptions.allowSyntheticDefaultImports != "()" ? compilerOptions.allowSyntheticDefaultImports : modulekind == ModuleKind.System

    val emitResolver = createResolver()

    val undefinedSymbol = createSymbol(SymbolFlags.Property | SymbolFlags.Transient, "()")
    undefinedSymbol.declarations = []
    val argumentsSymbol = createSymbol(SymbolFlags.Property | SymbolFlags.Transient, "arguments")

    val checker: TypeChecker = {
      getNodeCount: () => sum(host.getSourceFiles(), "nodeCount"),
      getIdentifierCount: () => sum(host.getSourceFiles(), "identifierCount"),
      getSymbolCount: () => sum(host.getSourceFiles(), "symbolCount") + symbolCount,
      getTypeCount: () => typeCount,
      isUndefinedSymbol: symbol => symbol == undefinedSymbol,
      isArgumentsSymbol: symbol => symbol == argumentsSymbol,
      isUnknownSymbol: symbol => symbol == unknownSymbol,
      getDiagnostics,
      getGlobalDiagnostics,

      // The language service will always care about the narrowed type of a symbol, because that is
      // the type the language says the symbol should have.
      getTypeOfSymbolAtLocation: getNarrowedTypeOfSymbol,
      getSymbolsOfParameterPropertyDeclaration,
      getDeclaredTypeOfSymbol,
      getPropertiesOfType,
      getPropertyOfType,
      getSignaturesOfType,
      getIndexTypeOfType,
      getBaseTypes,
      getReturnTypeOfSignature,
      getSymbolsInScope,
      getSymbolAtLocation,
      getShorthandAssignmentValueSymbol,
      getExportSpecifierLocalTargetSymbol,
      getTypeAtLocation: getTypeOfNode,
      typeToString,
      getSymbolDisplayBuilder,
      symbolToString,
      getAugmentedPropertiesOfType,
      getRootSymbols,
      getContextualType,
      getFullyQualifiedName,
      getResolvedSignature,
      getConstantValue,
      isValidPropertyAccess,
      getSignatureFromDeclaration,
      isImplementationOfOverload,
      getAliasedSymbol: resolveAlias,
      getEmitResolver,
      getExportsOfModule: getExportsOfModuleAsArray,

      getJsxElementAttributesType,
      getJsxIntrinsicTagNames,
      isOptionalParameter
    }

    val unknownSymbol = createSymbol(SymbolFlags.Property | SymbolFlags.Transient, "unknown")
    val resolvingSymbol = createSymbol(SymbolFlags.Transient, "__resolving__")

    val anyType = createIntrinsicType(TypeFlags.Any, "any")
    val stringType = createIntrinsicType(TypeFlags.String, "String")
    val numberType = createIntrinsicType(TypeFlags.Number, "Int")
    val booleanType = createIntrinsicType(TypeFlags.Boolean, "Boolean")
    val esSymbolType = createIntrinsicType(TypeFlags.ESSymbol, "symbol")
    val voidType = createIntrinsicType(TypeFlags.Void, "Unit")
    val undefinedType = createIntrinsicType(TypeFlags.Undefined | TypeFlags.ContainsUndefinedOrNull, "()")
    val nullType = createIntrinsicType(TypeFlags.Null | TypeFlags.ContainsUndefinedOrNull, "null")
    val unknownType = createIntrinsicType(TypeFlags.Any, "unknown")

    val emptyObjectType = createAnonymousType((), emptySymbols, emptyArray, emptyArray, (), ())
    val emptyUnionType = emptyObjectType
    val emptyGenericType = <GenericType><ObjectType>createAnonymousType((), emptySymbols, emptyArray, emptyArray, (), ())
    emptyGenericType.instantiations = {}

    val anyFunctionType = createAnonymousType((), emptySymbols, emptyArray, emptyArray, (), ())
    // The anyFunctionType contains the anyFunctionType by definition. The flag is further propagated
    // in getPropagatingFlagsOfTypes, and it is checked in inferFromTypes.
    anyFunctionType.flags |= TypeFlags.ContainsAnyFunctionType

    val noConstraintType = createAnonymousType((), emptySymbols, emptyArray, emptyArray, (), ())

    val anySignature = createSignature((), (), emptyArray, anyType, 0, /*hasRestParameter*/ false, /*hasStringLiterals*/ false)
    val unknownSignature = createSignature((), (), emptyArray, unknownType, 0, /*hasRestParameter*/ false, /*hasStringLiterals*/ false)

    val enumNumberIndexInfo = createIndexInfo(stringType, /*isReadonly*/ true)

    val globals: SymbolTable = {}

    var globalESSymbolConstructorSymbol: Symbol

    var getGlobalPromiseConstructorSymbol: () => Symbol

    var globalObjectType: ObjectType
    var globalFunctionType: ObjectType
    var globalArrayType: GenericType
    var globalReadonlyArrayType: GenericType
    var globalStringType: ObjectType
    var globalNumberType: ObjectType
    var globalBooleanType: ObjectType
    var globalRegExpType: ObjectType
    var globalTemplateStringsArrayType: ObjectType
    var globalESSymbolType: ObjectType
    var globalIterableType: GenericType
    var globalIteratorType: GenericType
    var globalIterableIteratorType: GenericType

    var anyArrayType: Type
    var anyReadonlyArrayType: Type
    var getGlobalClassDecoratorType: () => ObjectType
    var getGlobalParameterDecoratorType: () => ObjectType
    var getGlobalPropertyDecoratorType: () => ObjectType
    var getGlobalMethodDecoratorType: () => ObjectType
    var getGlobalTypedPropertyDescriptorType: () => ObjectType
    var getGlobalPromiseType: () => ObjectType
    var tryGetGlobalPromiseType: () => ObjectType
    var getGlobalPromiseLikeType: () => ObjectType
    var getInstantiatedGlobalPromiseLikeType: () => ObjectType
    var getGlobalPromiseConstructorLikeType: () => ObjectType
    var getGlobalThenableType: () => ObjectType

    var jsxElementClassType: Type

    var deferredNodes: Node[]

    val tupleTypes: Map<TupleType> = {}
    val unionTypes: Map<UnionType> = {}
    val intersectionTypes: Map<IntersectionType> = {}
    val stringLiteralTypes: Map<StringLiteralType> = {}

    val resolutionTargets: TypeSystemEntity[] = []
    val resolutionResults: Boolean[] = []
    val resolutionPropertyNames: TypeSystemPropertyName[] = []

    val mergedSymbols: Symbol[] = []
    val symbolLinks: SymbolLinks[] = []
    val nodeLinks: NodeLinks[] = []
    val potentialThisCollisions: Node[] = []
    val awaitedTypeStack: Int[] = []

    val diagnostics = createDiagnosticCollection()

    val primitiveTypeInfo: Map<{ type: Type; flags: TypeFlags }> = {
      "String": {
        type: stringType,
        flags: TypeFlags.StringLike
      },
      "Int": {
        type: numberType,
        flags: TypeFlags.NumberLike
      },
      "Boolean": {
        type: booleanType,
        flags: TypeFlags.Boolean
      },
      "symbol": {
        type: esSymbolType,
        flags: TypeFlags.ESSymbol
      },
      "()": {
        type: undefinedType,
        flags: TypeFlags.ContainsUndefinedOrNull
      }
    }

    var jsxElementType: ObjectType
    /** Things we lazy load from the JSX package */
    val jsxTypes: Map<ObjectType> = {}
    val JsxNames = {
      JSX: "JSX",
      IntrinsicElements: "IntrinsicElements",
      ElementClass: "ElementClass",
      ElementAttributesPropertyNameContainer: "ElementAttributesProperty",
      Element: "Element",
      IntrinsicAttributes: "IntrinsicAttributes",
      IntrinsicClassAttributes: "IntrinsicClassAttributes"
    }

    val subtypeRelation: Map<RelationComparisonResult> = {}
    val assignableRelation: Map<RelationComparisonResult> = {}
    val identityRelation: Map<RelationComparisonResult> = {}

    // This is for caching the result of getSymbolDisplayBuilder. Do not access directly.
    var _displayBuilder: SymbolDisplayBuilder

    type TypeSystemEntity = Symbol | Type | Signature

    val enum TypeSystemPropertyName {
      Type,
      ResolvedBaseConstructorType,
      DeclaredType,
      ResolvedReturnType
    }

    val builtinGlobals: SymbolTable = {
      [undefinedSymbol.name]: undefinedSymbol
    }

    initializeTypeChecker()

    return checker

    def getEmitResolver(sourceFile: SourceFile, cancellationToken: CancellationToken) {
      // Ensure we have all the type information in place for this file so that all the
      // emitter questions of this resolver will return the right information.
      getDiagnostics(sourceFile, cancellationToken)
      return emitResolver
    }

    def error(location: Node, message: DiagnosticMessage, arg0?: any, arg1?: any, arg2?: any): Unit {
      val diagnostic = location
        ? createDiagnosticForNode(location, message, arg0, arg1, arg2)
        : createCompilerDiagnostic(message, arg0, arg1, arg2)
      diagnostics.add(diagnostic)
    }

    def createSymbol(flags: SymbolFlags, name: String): Symbol {
      symbolCount++
      return new Symbol(flags, name)
    }

    def getExcludedSymbolFlags(flags: SymbolFlags): SymbolFlags {
      var result: SymbolFlags = 0
      if (flags & SymbolFlags.BlockScopedVariable) result |= SymbolFlags.BlockScopedVariableExcludes
      if (flags & SymbolFlags.FunctionScopedVariable) result |= SymbolFlags.FunctionScopedVariableExcludes
      if (flags & SymbolFlags.Property) result |= SymbolFlags.PropertyExcludes
      if (flags & SymbolFlags.EnumMember) result |= SymbolFlags.EnumMemberExcludes
      if (flags & SymbolFlags.Function) result |= SymbolFlags.FunctionExcludes
      if (flags & SymbolFlags.Class) result |= SymbolFlags.ClassExcludes
      if (flags & SymbolFlags.Interface) result |= SymbolFlags.InterfaceExcludes
      if (flags & SymbolFlags.RegularEnum) result |= SymbolFlags.RegularEnumExcludes
      if (flags & SymbolFlags.ConstEnum) result |= SymbolFlags.ConstEnumExcludes
      if (flags & SymbolFlags.ValueModule) result |= SymbolFlags.ValueModuleExcludes
      if (flags & SymbolFlags.Method) result |= SymbolFlags.MethodExcludes
      if (flags & SymbolFlags.GetAccessor) result |= SymbolFlags.GetAccessorExcludes
      if (flags & SymbolFlags.SetAccessor) result |= SymbolFlags.SetAccessorExcludes
      if (flags & SymbolFlags.TypeParameter) result |= SymbolFlags.TypeParameterExcludes
      if (flags & SymbolFlags.TypeAlias) result |= SymbolFlags.TypeAliasExcludes
      if (flags & SymbolFlags.Alias) result |= SymbolFlags.AliasExcludes
      return result
    }

    def recordMergedSymbol(target: Symbol, source: Symbol) {
      if (!source.mergeId) {
        source.mergeId = nextMergeId
        nextMergeId++
      }
      mergedSymbols[source.mergeId] = target
    }

    def cloneSymbol(symbol: Symbol): Symbol {
      val result = createSymbol(symbol.flags | SymbolFlags.Merged, symbol.name)
      result.declarations = symbol.declarations.slice(0)
      result.parent = symbol.parent
      if (symbol.valueDeclaration) result.valueDeclaration = symbol.valueDeclaration
      if (symbol.constEnumOnlyModule) result.constEnumOnlyModule = true
      if (symbol.members) result.members = cloneSymbolTable(symbol.members)
      if (symbol.exports) result.exports = cloneSymbolTable(symbol.exports)
      recordMergedSymbol(result, symbol)
      return result
    }

    def mergeSymbol(target: Symbol, source: Symbol) {
      if (!(target.flags & getExcludedSymbolFlags(source.flags))) {
        if (source.flags & SymbolFlags.ValueModule && target.flags & SymbolFlags.ValueModule && target.constEnumOnlyModule && !source.constEnumOnlyModule) {
          // reset flag when merging instantiated module into value module that has only val enums
          target.constEnumOnlyModule = false
        }
        target.flags |= source.flags
        if (source.valueDeclaration &&
          (!target.valueDeclaration ||
           (target.valueDeclaration.kind == SyntaxKind.ModuleDeclaration && source.valueDeclaration.kind != SyntaxKind.ModuleDeclaration))) {
          // other kinds of value declarations take precedence over modules
          target.valueDeclaration = source.valueDeclaration
        }
        forEach(source.declarations, node => {
          target.declarations.push(node)
        })
        if (source.members) {
          if (!target.members) target.members = {}
          mergeSymbolTable(target.members, source.members)
        }
        if (source.exports) {
          if (!target.exports) target.exports = {}
          mergeSymbolTable(target.exports, source.exports)
        }
        recordMergedSymbol(target, source)
      }
      else {
        val message = target.flags & SymbolFlags.BlockScopedVariable || source.flags & SymbolFlags.BlockScopedVariable
          ? Diagnostics.Cannot_redeclare_block_scoped_variable_0 : Diagnostics.Duplicate_identifier_0
        forEach(source.declarations, node => {
          error(node.name ? node.name : node, message, symbolToString(source))
        })
        forEach(target.declarations, node => {
          error(node.name ? node.name : node, message, symbolToString(source))
        })
      }
    }

    def cloneSymbolTable(symbolTable: SymbolTable): SymbolTable {
      val result: SymbolTable = {}
      for (val id in symbolTable) {
        if (hasProperty(symbolTable, id)) {
          result[id] = symbolTable[id]
        }
      }
      return result
    }

    def mergeSymbolTable(target: SymbolTable, source: SymbolTable) {
      for (val id in source) {
        if (hasProperty(source, id)) {
          if (!hasProperty(target, id)) {
            target[id] = source[id]
          }
          else {
            var symbol = target[id]
            if (!(symbol.flags & SymbolFlags.Merged)) {
              target[id] = symbol = cloneSymbol(symbol)
            }
            mergeSymbol(symbol, source[id])
          }
        }
      }
    }

    def mergeModuleAugmentation(moduleName: LiteralExpression): Unit {
      val moduleAugmentation = <ModuleDeclaration>moduleName.parent
      if (moduleAugmentation.symbol.valueDeclaration != moduleAugmentation) {
        // this is a combined symbol for multiple augmentations within the same file.
        // its symbol already has accumulated information for all declarations
        // so we need to add it just once - do the work only for first declaration
        Debug.assert(moduleAugmentation.symbol.declarations.length > 1)
        return
      }

      if (isGlobalScopeAugmentation(moduleAugmentation)) {
        mergeSymbolTable(globals, moduleAugmentation.symbol.exports)
      }
      else {
        // find a module that about to be augmented
        var mainModule = resolveExternalModuleNameWorker(moduleName, moduleName, Diagnostics.Invalid_module_name_in_augmentation_module_0_cannot_be_found)
        if (!mainModule) {
          return
        }
        // obtain item referenced by 'export='
        mainModule = resolveExternalModuleSymbol(mainModule)
        if (mainModule.flags & SymbolFlags.Namespace) {
          // if module symbol has already been merged - it is safe to use it.
          // otherwise clone it
          mainModule = mainModule.flags & SymbolFlags.Merged ? mainModule : cloneSymbol(mainModule)
          mergeSymbol(mainModule, moduleAugmentation.symbol)
        }
        else {
          error(moduleName, Diagnostics.Cannot_augment_module_0_because_it_resolves_to_a_non_module_entity, moduleName.text)
        }
      }
    }

    def addToSymbolTable(target: SymbolTable, source: SymbolTable, message: DiagnosticMessage) {
      for (val id in source) {
        if (hasProperty(source, id)) {
          if (hasProperty(target, id)) {
            // Error on redeclarations
            forEach(target[id].declarations, addDeclarationDiagnostic(id, message))
          }
          else {
            target[id] = source[id]
          }
        }
      }

      def addDeclarationDiagnostic(id: String, message: DiagnosticMessage) {
        return (declaration: Declaration) => diagnostics.add(createDiagnosticForNode(declaration, message, id))
      }
    }

    def getSymbolLinks(symbol: Symbol): SymbolLinks {
      if (symbol.flags & SymbolFlags.Transient) return <TransientSymbol>symbol
      val id = getSymbolId(symbol)
      return symbolLinks[id] || (symbolLinks[id] = {})
    }

    def getNodeLinks(node: Node): NodeLinks {
      val nodeId = getNodeId(node)
      return nodeLinks[nodeId] || (nodeLinks[nodeId] = {})
    }

    def isGlobalSourceFile(node: Node) {
      return node.kind == SyntaxKind.SourceFile && !isExternalOrCommonJsModule(<SourceFile>node)
    }

    def getSymbol(symbols: SymbolTable, name: String, meaning: SymbolFlags): Symbol {
      if (meaning && hasProperty(symbols, name)) {
        val symbol = symbols[name]
        Debug.assert((symbol.flags & SymbolFlags.Instantiated) == 0, "Should never get an instantiated symbol here.")
        if (symbol.flags & meaning) {
          return symbol
        }
        if (symbol.flags & SymbolFlags.Alias) {
          val target = resolveAlias(symbol)
          // Unknown symbol means an error occurred in alias resolution, treat it as positive answer to avoid cascading errors
          if (target == unknownSymbol || target.flags & meaning) {
            return symbol
          }
        }
      }
      // return () if we can't find a symbol.
    }

    /**
     * Get symbols that represent parameter-property-declaration as parameter and as property declaration
     * @param parameter a parameterDeclaration node
     * @param parameterName a name of the parameter to get the symbols for.
     * @return a tuple of two symbols
     */
    def getSymbolsOfParameterPropertyDeclaration(parameter: ParameterDeclaration, parameterName: String): [Symbol, Symbol] {
      val constructorDeclaration = parameter.parent
      val classDeclaration = parameter.parent.parent

      val parameterSymbol = getSymbol(constructorDeclaration.locals, parameterName, SymbolFlags.Value)
      val propertySymbol = getSymbol(classDeclaration.symbol.members, parameterName, SymbolFlags.Value)

      if (parameterSymbol && propertySymbol) {
        return [parameterSymbol, propertySymbol]
      }

      Debug.fail("There should exist two symbols, one as property declaration and one as parameter declaration")
    }

    def isBlockScopedNameDeclaredBeforeUse(declaration: Declaration, usage: Node): Boolean {
      val declarationFile = getSourceFileOfNode(declaration)
      val useFile = getSourceFileOfNode(usage)
      if (declarationFile != useFile) {
        if (modulekind || (!compilerOptions.outFile && !compilerOptions.out)) {
          // nodes are in different files and order cannot be determines
          return true
        }

        val sourceFiles = host.getSourceFiles()
        return indexOf(sourceFiles, declarationFile) <= indexOf(sourceFiles, useFile)
      }

      if (declaration.pos <= usage.pos) {
        // declaration is before usage
        // still might be illegal if usage is in the initializer of the variable declaration
        return declaration.kind != SyntaxKind.VariableDeclaration ||
          !isImmediatelyUsedInInitializerOfBlockScopedVariable(<VariableDeclaration>declaration, usage)
      }

      // declaration is after usage
      // can be legal if usage is deferred (i.e. inside def or in initializer of instance property)
      return isUsedInFunctionOrNonStaticProperty(declaration, usage)

      def isImmediatelyUsedInInitializerOfBlockScopedVariable(declaration: VariableDeclaration, usage: Node): Boolean {
        val container = getEnclosingBlockScopeContainer(declaration)

        if (declaration.parent.parent.kind == SyntaxKind.VariableStatement ||
          declaration.parent.parent.kind == SyntaxKind.ForStatement) {
          // variable statement/for statement case,
          // use site should not be inside variable declaration (initializer of declaration or binding element)
          return isSameScopeDescendentOf(usage, declaration, container)
        }
        else if (declaration.parent.parent.kind == SyntaxKind.ForOfStatement ||
          declaration.parent.parent.kind == SyntaxKind.ForInStatement) {
          // ForIn/ForOf case - use site should not be used in expression part
          val expression = (<ForInStatement | ForOfStatement>declaration.parent.parent).expression
          return isSameScopeDescendentOf(usage, expression, container)
        }
      }

      def isUsedInFunctionOrNonStaticProperty(declaration: Declaration, usage: Node): Boolean {
        val container = getEnclosingBlockScopeContainer(declaration)
        var current = usage
        while (current) {
          if (current == container) {
            return false
          }

          if (isFunctionLike(current)) {
            return true
          }

          val initializerOfNonStaticProperty = current.parent &&
            current.parent.kind == SyntaxKind.PropertyDeclaration &&
            (current.parent.flags & NodeFlags.Static) == 0 &&
            (<PropertyDeclaration>current.parent).initializer == current

          if (initializerOfNonStaticProperty) {
            return true
          }

          current = current.parent
        }
        return false
      }
    }

    // Resolve a given name for a given meaning at a given location. An error is reported if the name was not found and
    // the nameNotFoundMessage argument is not (). Returns the resolved symbol, or () if no symbol with
    // the given name can be found.
    def resolveName(location: Node, name: String, meaning: SymbolFlags, nameNotFoundMessage: DiagnosticMessage, nameArg: String | Identifier): Symbol {
      var result: Symbol
      var lastLocation: Node
      var propertyWithInvalidInitializer: Node
      val errorLocation = location
      var grandparent: Node

      loop: while (location) {
        // Locals of a source file are not in scope (because they get merged into the global symbol table)
        if (location.locals && !isGlobalSourceFile(location)) {
          if (result = getSymbol(location.locals, name, meaning)) {
            var useResult = true
            if (isFunctionLike(location) && lastLocation && lastLocation != (<FunctionLikeDeclaration>location).body) {
              // symbol lookup restrictions for def-like declarations
              // - Type parameters of a def are in scope in the entire def declaration, including the parameter
              //   list and return type. However, local types are only in scope in the def body.
              // - parameters are only in the scope of def body
              // This restriction does not apply to JSDoc comment types because they are parented
              // at a higher level than type parameters would normally be
              if (meaning & result.flags & SymbolFlags.Type && lastLocation.kind != SyntaxKind.JSDocComment) {
                useResult = result.flags & SymbolFlags.TypeParameter
                  // type parameters are visible in parameter list, return type and type parameter list
                  ? lastLocation == (<FunctionLikeDeclaration>location).type ||
                    lastLocation.kind == SyntaxKind.Parameter ||
                    lastLocation.kind == SyntaxKind.TypeParameter
                  // local types not visible outside the def body
                  : false
              }
              if (meaning & SymbolFlags.Value && result.flags & SymbolFlags.FunctionScopedVariable) {
                // parameters are visible only inside def body, parameter list and return type
                // technically for parameter list case here we might mix parameters and variables declared in def,
                // however it is detected separately when checking initializers of parameters
                // to make sure that they reference no variables declared after them.
                useResult =
                  lastLocation.kind == SyntaxKind.Parameter ||
                  (
                    lastLocation == (<FunctionLikeDeclaration>location).type &&
                    result.valueDeclaration.kind == SyntaxKind.Parameter
                  )
              }
            }

            if (useResult) {
              break loop
            }
            else {
              result = ()
            }
          }
        }
        switch (location.kind) {
          case SyntaxKind.SourceFile:
            if (!isExternalOrCommonJsModule(<SourceFile>location)) break
          case SyntaxKind.ModuleDeclaration:
            val moduleExports = getSymbolOfNode(location).exports
            if (location.kind == SyntaxKind.SourceFile || isAmbientModule(location)) {

              // It's an external module. First see if the module has an default and if the local
              // name of that default matches.
              if (result = moduleExports["default"]) {
                val localSymbol = getLocalSymbolForExportDefault(result)
                if (localSymbol && (result.flags & meaning) && localSymbol.name == name) {
                  break loop
                }
                result = ()
              }

              // Because of module/package merging, a module's exports are in scope,
              // yet we never want to treat an specifier as putting a member in scope.
              // Therefore, if the name we find is purely an specifier, it is not actually considered in scope.
              // Two things to note about this:
              //   1. We have to check this without calling getSymbol. The problem with calling getSymbol
              //    on an specifier is that it might find the specifier itself, and try to
              //    resolve it as an alias. This will cause the checker to consider the specifier
              //    a circular alias reference when it might not be.
              //   2. We check == SymbolFlags.Alias in order to check that the symbol is *purely*
              //    an alias. If we used &, we'd be throwing out symbols that have non alias aspects,
              //    which is not the desired behavior.
              if (hasProperty(moduleExports, name) &&
                moduleExports[name].flags == SymbolFlags.Alias &&
                getDeclarationOfKind(moduleExports[name], SyntaxKind.ExportSpecifier)) {
                break
              }
            }

            if (result = getSymbol(moduleExports, name, meaning & SymbolFlags.ModuleMember)) {
              break loop
            }
            break
          case SyntaxKind.EnumDeclaration:
            if (result = getSymbol(getSymbolOfNode(location).exports, name, meaning & SymbolFlags.EnumMember)) {
              break loop
            }
            break
          case SyntaxKind.PropertyDeclaration:
          case SyntaxKind.PropertySignature:
            // TypeScript 1.0 spec (April 2014): 8.4.1
            // Initializer expressions for instance member variables are evaluated in the scope
            // of the class constructor body but are not permitted to reference parameters or
            // local variables of the constructor. This effectively means that entities from outer scopes
            // by the same name as a constructor parameter or local variable are inaccessible
            // in initializer expressions for instance member variables.
            if (isClassLike(location.parent) && !(location.flags & NodeFlags.Static)) {
              val ctor = findConstructorDeclaration(<ClassLikeDeclaration>location.parent)
              if (ctor && ctor.locals) {
                if (getSymbol(ctor.locals, name, meaning & SymbolFlags.Value)) {
                  // Remember the property node, it will be used later to report appropriate error
                  propertyWithInvalidInitializer = location
                }
              }
            }
            break
          case SyntaxKind.ClassDeclaration:
          case SyntaxKind.ClassExpression:
          case SyntaxKind.InterfaceDeclaration:
            if (result = getSymbol(getSymbolOfNode(location).members, name, meaning & SymbolFlags.Type)) {
              if (lastLocation && lastLocation.flags & NodeFlags.Static) {
                // TypeScript 1.0 spec (April 2014): 3.4.1
                // The scope of a type parameter extends over the entire declaration with which the type
                // parameter list is associated, with the exception of static member declarations in classes.
                error(errorLocation, Diagnostics.Static_members_cannot_reference_class_type_parameters)
                return ()
              }
              break loop
            }
            if (location.kind == SyntaxKind.ClassExpression && meaning & SymbolFlags.Class) {
              val className = (<ClassExpression>location).name
              if (className && name == className.text) {
                result = location.symbol
                break loop
              }
            }
            break

          // It is not legal to reference a class's own type parameters from a computed property name that
          // belongs to the class. For example:
          //
          //   def foo<T>() { return '' }
          //   class C<T> { // <-- Class's own type parameter T
          //     [foo<T>()]() { } // <-- Reference to T from class's own computed property
          //   }
          //
          case SyntaxKind.ComputedPropertyName:
            grandparent = location.parent.parent
            if (isClassLike(grandparent) || grandparent.kind == SyntaxKind.InterfaceDeclaration) {
              // A reference to this grandparent's type parameters would be an error
              if (result = getSymbol(getSymbolOfNode(grandparent).members, name, meaning & SymbolFlags.Type)) {
                error(errorLocation, Diagnostics.A_computed_property_name_cannot_reference_a_type_parameter_from_its_containing_type)
                return ()
              }
            }
            break
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.MethodSignature:
          case SyntaxKind.Constructor:
          case SyntaxKind.GetAccessor:
          case SyntaxKind.SetAccessor:
          case SyntaxKind.FunctionDeclaration:
          case SyntaxKind.ArrowFunction:
            if (meaning & SymbolFlags.Variable && name == "arguments") {
              result = argumentsSymbol
              break loop
            }
            break
          case SyntaxKind.FunctionExpression:
            if (meaning & SymbolFlags.Variable && name == "arguments") {
              result = argumentsSymbol
              break loop
            }

            if (meaning & SymbolFlags.Function) {
              val functionName = (<FunctionExpression>location).name
              if (functionName && name == functionName.text) {
                result = location.symbol
                break loop
              }
            }
            break
          case SyntaxKind.Decorator:
            // Decorators are resolved at the class declaration. Resolving at the parameter
            // or member would result in looking up locals in the method.
            //
            //   def y() {}
            //   class C {
            //     method(@y x, y) {} // <-- decorator y should be resolved at the class declaration, not the parameter.
            //   }
            //
            if (location.parent && location.parent.kind == SyntaxKind.Parameter) {
              location = location.parent
            }
            //
            //   def y() {}
            //   class C {
            //     @y method(x, y) {} // <-- decorator y should be resolved at the class declaration, not the method.
            //   }
            //
            if (location.parent && isClassElement(location.parent)) {
              location = location.parent
            }
            break
        }
        lastLocation = location
        location = location.parent
      }

      if (!result) {
        result = getSymbol(globals, name, meaning)
      }

      if (!result) {
        if (nameNotFoundMessage) {
          if (!checkAndReportErrorForMissingPrefix(errorLocation, name, nameArg)) {
            error(errorLocation, nameNotFoundMessage, typeof nameArg == "String" ? nameArg : declarationNameToString(nameArg))
          }
        }
        return ()
      }

      // Perform extra checks only if error reporting was requested
      if (nameNotFoundMessage) {
        if (propertyWithInvalidInitializer) {
          // We have a match, but the reference occurred within a property initializer and the identifier also binds
          // to a local variable in the constructor where the code will be emitted.
          val propertyName = (<PropertyDeclaration>propertyWithInvalidInitializer).name
          error(errorLocation, Diagnostics.Initializer_of_instance_member_variable_0_cannot_reference_identifier_1_declared_in_the_constructor,
            declarationNameToString(propertyName), typeof nameArg == "String" ? nameArg : declarationNameToString(nameArg))
          return ()
        }

        // Only check for block-scoped variable if we are looking for the
        // name with variable meaning
        //    For example,
        //      declare module foo {
        //        trait bar {}
        //      }
        //    val foo/*1*/: foo/*2*/.bar
        // The foo at /*1*/ and /*2*/ will share same symbol with two meaning
        // block - scope variable and package module. However, only when we
        // try to resolve name in /*1*/ which is used in variable position,
        // we want to check for block- scoped
        if (meaning & SymbolFlags.BlockScopedVariable) {
          val exportOrLocalSymbol = getExportSymbolOfValueSymbolIfExported(result)
          if (exportOrLocalSymbol.flags & SymbolFlags.BlockScopedVariable) {
            checkResolvedBlockScopedVariable(exportOrLocalSymbol, errorLocation)
          }
        }
      }
      return result
    }

    def checkAndReportErrorForMissingPrefix(errorLocation: Node, name: String, nameArg: String | Identifier): Boolean {
      if (!errorLocation || (errorLocation.kind == SyntaxKind.Identifier && (isTypeReferenceIdentifier(<Identifier>errorLocation)) || isInTypeQuery(errorLocation))) {
        return false
      }

      val container = getThisContainer(errorLocation, /* includeArrowFunctions */ true)
      var location = container
      while (location) {
        if (isClassLike(location.parent)) {
          val classSymbol = getSymbolOfNode(location.parent)
          if (!classSymbol) {
            break
          }

          // Check to see if a static member exists.
          val constructorType = getTypeOfSymbol(classSymbol)
          if (getPropertyOfType(constructorType, name)) {
            error(errorLocation, Diagnostics.Cannot_find_name_0_Did_you_mean_the_static_member_1_0, typeof nameArg == "String" ? nameArg : declarationNameToString(nameArg), symbolToString(classSymbol))
            return true
          }

          // No static member is present.
          // Check if we're in an instance method and look for a relevant instance member.
          if (location == container && !(location.flags & NodeFlags.Static)) {
            val instanceType = (<InterfaceType>getDeclaredTypeOfSymbol(classSymbol)).thisType
            if (getPropertyOfType(instanceType, name)) {
              error(errorLocation, Diagnostics.Cannot_find_name_0_Did_you_mean_the_instance_member_this_0, typeof nameArg == "String" ? nameArg : declarationNameToString(nameArg))
              return true
            }
          }
        }

        location = location.parent
      }
      return false
    }

    def checkResolvedBlockScopedVariable(result: Symbol, errorLocation: Node): Unit {
      Debug.assert((result.flags & SymbolFlags.BlockScopedVariable) != 0)
      // Block-scoped variables cannot be used before their definition
      val declaration = forEach(result.declarations, d => isBlockOrCatchScoped(d) ? d : ())

      Debug.assert(declaration != (), "Block-scoped variable declaration is ()")

      if (!isBlockScopedNameDeclaredBeforeUse(<Declaration>getAncestor(declaration, SyntaxKind.VariableDeclaration), errorLocation)) {
        error(errorLocation, Diagnostics.Block_scoped_variable_0_used_before_its_declaration, declarationNameToString(declaration.name))
      }
    }

    /* Starting from 'initial' node walk up the parent chain until 'stopAt' node is reached.
     * If at any point current node is equal to 'parent' node - return true.
     * Return false if 'stopAt' node is reached or isFunctionLike(current) == true.
     */
    def isSameScopeDescendentOf(initial: Node, parent: Node, stopAt: Node): Boolean {
      if (!parent) {
        return false
      }
      for (var current = initial; current && current != stopAt && !isFunctionLike(current); current = current.parent) {
        if (current == parent) {
          return true
        }
      }
      return false
    }

    def getAnyImportSyntax(node: Node): AnyImportSyntax {
      if (isAliasSymbolDeclaration(node)) {
        if (node.kind == SyntaxKind.ImportEqualsDeclaration) {
          return <ImportEqualsDeclaration>node
        }

        while (node && node.kind != SyntaxKind.ImportDeclaration) {
          node = node.parent
        }
        return <ImportDeclaration>node
      }
    }

    def getDeclarationOfAliasSymbol(symbol: Symbol): Declaration {
      return forEach(symbol.declarations, d => isAliasSymbolDeclaration(d) ? d : ())
    }

    def getTargetOfImportEqualsDeclaration(node: ImportEqualsDeclaration): Symbol {
      if (node.moduleReference.kind == SyntaxKind.ExternalModuleReference) {
        return resolveExternalModuleSymbol(resolveExternalModuleName(node, getExternalModuleImportEqualsDeclarationExpression(node)))
      }
      return getSymbolOfPartOfRightHandSideOfImportEquals(<EntityName>node.moduleReference, node)
    }

    def getTargetOfImportClause(node: ImportClause): Symbol {
      val moduleSymbol = resolveExternalModuleName(node, (<ImportDeclaration>node.parent).moduleSpecifier)
      if (moduleSymbol) {
        val exportDefaultSymbol = resolveSymbol(moduleSymbol.exports["default"])
        if (!exportDefaultSymbol && !allowSyntheticDefaultImports) {
          error(node.name, Diagnostics.Module_0_has_no_default_export, symbolToString(moduleSymbol))
        }
        else if (!exportDefaultSymbol && allowSyntheticDefaultImports) {
          return resolveExternalModuleSymbol(moduleSymbol) || resolveSymbol(moduleSymbol)
        }
        return exportDefaultSymbol
      }
    }

    def getTargetOfNamespaceImport(node: NamespaceImport): Symbol {
      val moduleSpecifier = (<ImportDeclaration>node.parent.parent).moduleSpecifier
      return resolveESModuleSymbol(resolveExternalModuleName(node, moduleSpecifier), moduleSpecifier)
    }

    // This def creates a synthetic symbol that combines the value side of one symbol with the
    // type/package side of another symbol. Consider this example:
    //
    //   declare module graphics {
    //     trait Point {
    //       x: Int
    //       y: Int
    //     }
    //   }
    //   declare var graphics: {
    //     Point: new (x: Int, y: Int) => graphics.Point
    //   }
    //   declare module "graphics" {
    //     = graphics
    //   }
    //
    // An 'import { Point } from "graphics"' needs to create a symbol that combines the value side 'Point'
    // property with the type/package side trait 'Point'.
    def combineValueAndTypeSymbols(valueSymbol: Symbol, typeSymbol: Symbol): Symbol {
      if (valueSymbol.flags & (SymbolFlags.Type | SymbolFlags.Namespace)) {
        return valueSymbol
      }
      val result = createSymbol(valueSymbol.flags | typeSymbol.flags, valueSymbol.name)
      result.declarations = concatenate(valueSymbol.declarations, typeSymbol.declarations)
      result.parent = valueSymbol.parent || typeSymbol.parent
      if (valueSymbol.valueDeclaration) result.valueDeclaration = valueSymbol.valueDeclaration
      if (typeSymbol.members) result.members = typeSymbol.members
      if (valueSymbol.exports) result.exports = valueSymbol.exports
      return result
    }

    def getExportOfModule(symbol: Symbol, name: String): Symbol {
      if (symbol.flags & SymbolFlags.Module) {
        val exports = getExportsOfSymbol(symbol)
        if (hasProperty(exports, name)) {
          return resolveSymbol(exports[name])
        }
      }
    }

    def getPropertyOfVariable(symbol: Symbol, name: String): Symbol {
      if (symbol.flags & SymbolFlags.Variable) {
        val typeAnnotation = (<VariableDeclaration>symbol.valueDeclaration).type
        if (typeAnnotation) {
          return resolveSymbol(getPropertyOfType(getTypeFromTypeNode(typeAnnotation), name))
        }
      }
    }

    def getExternalModuleMember(node: ImportDeclaration | ExportDeclaration, specifier: ImportOrExportSpecifier): Symbol {
      val moduleSymbol = resolveExternalModuleName(node, node.moduleSpecifier)
      val targetSymbol = resolveESModuleSymbol(moduleSymbol, node.moduleSpecifier)
      if (targetSymbol) {
        val name = specifier.propertyName || specifier.name
        if (name.text) {
          val symbolFromModule = getExportOfModule(targetSymbol, name.text)
          val symbolFromVariable = getPropertyOfVariable(targetSymbol, name.text)
          val symbol = symbolFromModule && symbolFromVariable ?
            combineValueAndTypeSymbols(symbolFromVariable, symbolFromModule) :
            symbolFromModule || symbolFromVariable
          if (!symbol) {
            error(name, Diagnostics.Module_0_has_no_exported_member_1, getFullyQualifiedName(moduleSymbol), declarationNameToString(name))
          }
          return symbol
        }
      }
    }

    def getTargetOfImportSpecifier(node: ImportSpecifier): Symbol {
      return getExternalModuleMember(<ImportDeclaration>node.parent.parent.parent, node)
    }

    def getTargetOfExportSpecifier(node: ExportSpecifier): Symbol {
      return (<ExportDeclaration>node.parent.parent).moduleSpecifier ?
        getExternalModuleMember(<ExportDeclaration>node.parent.parent, node) :
        resolveEntityName(node.propertyName || node.name, SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace)
    }

    def getTargetOfExportAssignment(node: ExportAssignment): Symbol {
      return resolveEntityName(<Identifier>node.expression, SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace)
    }

    def getTargetOfAliasDeclaration(node: Declaration): Symbol {
      switch (node.kind) {
        case SyntaxKind.ImportEqualsDeclaration:
          return getTargetOfImportEqualsDeclaration(<ImportEqualsDeclaration>node)
        case SyntaxKind.ImportClause:
          return getTargetOfImportClause(<ImportClause>node)
        case SyntaxKind.NamespaceImport:
          return getTargetOfNamespaceImport(<NamespaceImport>node)
        case SyntaxKind.ImportSpecifier:
          return getTargetOfImportSpecifier(<ImportSpecifier>node)
        case SyntaxKind.ExportSpecifier:
          return getTargetOfExportSpecifier(<ExportSpecifier>node)
        case SyntaxKind.ExportAssignment:
          return getTargetOfExportAssignment(<ExportAssignment>node)
      }
    }

    def resolveSymbol(symbol: Symbol): Symbol {
      return symbol && symbol.flags & SymbolFlags.Alias && !(symbol.flags & (SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace)) ? resolveAlias(symbol) : symbol
    }

    def resolveAlias(symbol: Symbol): Symbol {
      Debug.assert((symbol.flags & SymbolFlags.Alias) != 0, "Should only get Alias here.")
      val links = getSymbolLinks(symbol)
      if (!links.target) {
        links.target = resolvingSymbol
        val node = getDeclarationOfAliasSymbol(symbol)
        val target = getTargetOfAliasDeclaration(node)
        if (links.target == resolvingSymbol) {
          links.target = target || unknownSymbol
        }
        else {
          error(node, Diagnostics.Circular_definition_of_import_alias_0, symbolToString(symbol))
        }
      }
      else if (links.target == resolvingSymbol) {
        links.target = unknownSymbol
      }
      return links.target
    }

    def markExportAsReferenced(node: ImportEqualsDeclaration | ExportAssignment | ExportSpecifier) {
      val symbol = getSymbolOfNode(node)
      val target = resolveAlias(symbol)
      if (target) {
        val markAlias =
          (target == unknownSymbol && compilerOptions.isolatedModules) ||
          (target != unknownSymbol && (target.flags & SymbolFlags.Value) && !isConstEnumOrConstEnumOnlyModule(target))

        if (markAlias) {
          markAliasSymbolAsReferenced(symbol)
        }
      }
    }

    // When an alias symbol is referenced, we need to mark the entity it references as referenced and in turn repeat that until
    // we reach a non-alias or an exported entity (which is always considered referenced). We do this by checking the target of
    // the alias as an expression (which recursively takes us back here if the target references another alias).
    def markAliasSymbolAsReferenced(symbol: Symbol) {
      val links = getSymbolLinks(symbol)
      if (!links.referenced) {
        links.referenced = true
        val node = getDeclarationOfAliasSymbol(symbol)
        if (node.kind == SyntaxKind.ExportAssignment) {
          // default <symbol>
          checkExpressionCached((<ExportAssignment>node).expression)
        }
        else if (node.kind == SyntaxKind.ExportSpecifier) {
          // { <symbol> } or { <symbol> as foo }
          checkExpressionCached((<ExportSpecifier>node).propertyName || (<ExportSpecifier>node).name)
        }
        else if (isInternalModuleImportEqualsDeclaration(node)) {
          // import foo = <symbol>
          checkExpressionCached(<Expression>(<ImportEqualsDeclaration>node).moduleReference)
        }
      }
    }

    // This def is only for imports with entity names
    def getSymbolOfPartOfRightHandSideOfImportEquals(entityName: EntityName, importDeclaration?: ImportEqualsDeclaration): Symbol {
      if (!importDeclaration) {
        importDeclaration = <ImportEqualsDeclaration>getAncestor(entityName, SyntaxKind.ImportEqualsDeclaration)
        Debug.assert(importDeclaration != ())
      }
      // There are three things we might try to look for. In the following examples,
      // the search term is enclosed in |...|:
      //
      //   import a = |b|; // Namespace
      //   import a = |b.c|; // Value, type, package
      //   import a = |b.c|.d; // Namespace
      if (entityName.kind == SyntaxKind.Identifier && isRightSideOfQualifiedNameOrPropertyAccess(entityName)) {
        entityName = <QualifiedName>entityName.parent
      }
      // Check for case 1 and 3 in the above example
      if (entityName.kind == SyntaxKind.Identifier || entityName.parent.kind == SyntaxKind.QualifiedName) {
        return resolveEntityName(entityName, SymbolFlags.Namespace)
      }
      else {
        // Case 2 in above example
        // entityName.kind could be a QualifiedName or a Missing identifier
        Debug.assert(entityName.parent.kind == SyntaxKind.ImportEqualsDeclaration)
        return resolveEntityName(entityName, SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace)
      }
    }

    def getFullyQualifiedName(symbol: Symbol): String {
      return symbol.parent ? getFullyQualifiedName(symbol.parent) + "." + symbolToString(symbol) : symbolToString(symbol)
    }

    // Resolves a qualified name and any involved aliases
    def resolveEntityName(name: EntityName | Expression, meaning: SymbolFlags, ignoreErrors?: Boolean): Symbol {
      if (nodeIsMissing(name)) {
        return ()
      }

      var symbol: Symbol
      if (name.kind == SyntaxKind.Identifier) {
        val message = meaning == SymbolFlags.Namespace ? Diagnostics.Cannot_find_namespace_0 : Diagnostics.Cannot_find_name_0

        symbol = resolveName(name, (<Identifier>name).text, meaning, ignoreErrors ? () : message, <Identifier>name)
        if (!symbol) {
          return ()
        }
      }
      else if (name.kind == SyntaxKind.QualifiedName || name.kind == SyntaxKind.PropertyAccessExpression) {
        val left = name.kind == SyntaxKind.QualifiedName ? (<QualifiedName>name).left : (<PropertyAccessExpression>name).expression
        val right = name.kind == SyntaxKind.QualifiedName ? (<QualifiedName>name).right : (<PropertyAccessExpression>name).name

        val package = resolveEntityName(left, SymbolFlags.Namespace, ignoreErrors)
        if (!package || package == unknownSymbol || nodeIsMissing(right)) {
          return ()
        }
        symbol = getSymbol(getExportsOfSymbol(package), right.text, meaning)
        if (!symbol) {
          if (!ignoreErrors) {
            error(right, Diagnostics.Module_0_has_no_exported_member_1, getFullyQualifiedName(package), declarationNameToString(right))
          }
          return ()
        }
      }
      else {
        Debug.fail("Unknown entity name kind.")
      }
      Debug.assert((symbol.flags & SymbolFlags.Instantiated) == 0, "Should never get an instantiated symbol here.")
      return symbol.flags & meaning ? symbol : resolveAlias(symbol)
    }

    def resolveExternalModuleName(location: Node, moduleReferenceExpression: Expression): Symbol {
      return resolveExternalModuleNameWorker(location, moduleReferenceExpression, Diagnostics.Cannot_find_module_0)
    }

    def resolveExternalModuleNameWorker(location: Node, moduleReferenceExpression: Expression, moduleNotFoundError: DiagnosticMessage): Symbol {
      if (moduleReferenceExpression.kind != SyntaxKind.StringLiteral) {
        return
      }

      val moduleReferenceLiteral = <LiteralExpression>moduleReferenceExpression

      // Module names are escaped in our symbol table.  However, String literal values aren't.
      // Escape the name in the "require(...)" clause to ensure we find the right symbol.
      val moduleName = escapeIdentifier(moduleReferenceLiteral.text)

      if (moduleName == ()) {
        return
      }

      val isRelative = isExternalModuleNameRelative(moduleName)
      if (!isRelative) {
        val symbol = getSymbol(globals, "\"" + moduleName + "\"", SymbolFlags.ValueModule)
        if (symbol) {
          // merged symbol is module declaration symbol combined with all augmentations
          return getMergedSymbol(symbol)
        }
      }

      val resolvedModule = getResolvedModule(getSourceFileOfNode(location), moduleReferenceLiteral.text)
      val sourceFile = resolvedModule && host.getSourceFile(resolvedModule.resolvedFileName)
      if (sourceFile) {
        if (sourceFile.symbol) {
          // merged symbol is module declaration symbol combined with all augmentations
          return getMergedSymbol(sourceFile.symbol)
        }
        if (moduleNotFoundError) {
          // report errors only if it was requested
          error(moduleReferenceLiteral, Diagnostics.File_0_is_not_a_module, sourceFile.fileName)
        }
        return ()
      }
      if (moduleNotFoundError) {
        // report errors only if it was requested
        error(moduleReferenceLiteral, moduleNotFoundError, moduleName)
      }
      return ()
    }

    // An external module with an 'export =' declaration resolves to the target of the 'export =' declaration,
    // and an external module with no 'export =' declaration resolves to the module itself.
    def resolveExternalModuleSymbol(moduleSymbol: Symbol): Symbol {
      return moduleSymbol && getMergedSymbol(resolveSymbol(moduleSymbol.exports["export="])) || moduleSymbol
    }

    // An external module with an 'export =' declaration may be referenced as an ES6 module provided the 'export ='
    // references a symbol that is at least declared as a module or a variable. The target of the 'export =' may
    // combine other declarations with the module or variable (e.g. a class/module, def/module, trait/variable).
    def resolveESModuleSymbol(moduleSymbol: Symbol, moduleReferenceExpression: Expression): Symbol {
      var symbol = resolveExternalModuleSymbol(moduleSymbol)
      if (symbol && !(symbol.flags & (SymbolFlags.Module | SymbolFlags.Variable))) {
        error(moduleReferenceExpression, Diagnostics.Module_0_resolves_to_a_non_module_entity_and_cannot_be_imported_using_this_construct, symbolToString(moduleSymbol))
        symbol = ()
      }
      return symbol
    }

    def hasExportAssignmentSymbol(moduleSymbol: Symbol): Boolean {
      return moduleSymbol.exports["export="] != ()
    }

    def getExportsOfModuleAsArray(moduleSymbol: Symbol): Symbol[] {
      return symbolsToArray(getExportsOfModule(moduleSymbol))
    }

    def getExportsOfSymbol(symbol: Symbol): SymbolTable {
      return symbol.flags & SymbolFlags.Module ? getExportsOfModule(symbol) : symbol.exports || emptySymbols
    }

    def getExportsOfModule(moduleSymbol: Symbol): SymbolTable {
      val links = getSymbolLinks(moduleSymbol)
      return links.resolvedExports || (links.resolvedExports = getExportsForModule(moduleSymbol))
    }

    trait ExportCollisionTracker {
      specifierText: String
      exportsWithDuplicate: ExportDeclaration[]
    }

    /**
     * Extends one symbol table with another while collecting information on name collisions for error message generation into the `lookupTable` argument
     * Not passing `lookupTable` and `exportNode` disables this collection, and just extends the tables
     */
    def extendExportSymbols(target: SymbolTable, source: SymbolTable, lookupTable?: Map<ExportCollisionTracker>, exportNode?: ExportDeclaration) {
      for (val id in source) {
        if (id != "default" && !hasProperty(target, id)) {
          target[id] = source[id]
          if (lookupTable && exportNode) {
            lookupTable[id] = {
              specifierText: getTextOfNode(exportNode.moduleSpecifier)
            } as ExportCollisionTracker
          }
        }
        else if (lookupTable && exportNode && id != "default" && hasProperty(target, id) && resolveSymbol(target[id]) != resolveSymbol(source[id])) {
          if (!lookupTable[id].exportsWithDuplicate) {
            lookupTable[id].exportsWithDuplicate = [exportNode]
          }
          else {
            lookupTable[id].exportsWithDuplicate.push(exportNode)
          }
        }
      }
    }

    def getExportsForModule(moduleSymbol: Symbol): SymbolTable {
      val visitedSymbols: Symbol[] = []
      return visit(moduleSymbol) || moduleSymbol.exports

      // The ES6 spec permits * declarations in a module to circularly reference the module itself. For example,
      // module 'a' can 'export * from "b"' and 'b' can 'export * from "a"' without error.
      def visit(symbol: Symbol): SymbolTable {
        if (!(symbol && symbol.flags & SymbolFlags.HasExports && !contains(visitedSymbols, symbol))) {
          return
        }
        visitedSymbols.push(symbol)
        val symbols = cloneSymbolTable(symbol.exports)
        // All * declarations are collected in an __export symbol by the binder
        val exportStars = symbol.exports["__export"]
        if (exportStars) {
          val nestedSymbols: SymbolTable = {}
          val lookupTable: Map<ExportCollisionTracker> = {}
          for (val node of exportStars.declarations) {
            val resolvedModule = resolveExternalModuleName(node, (node as ExportDeclaration).moduleSpecifier)
            val exportedSymbols = visit(resolvedModule)
            extendExportSymbols(
              nestedSymbols,
              exportedSymbols,
              lookupTable,
              node as ExportDeclaration
            )
          }
          for (val id in lookupTable) {
            val { exportsWithDuplicate } = lookupTable[id]
            // It's not an error if the file with multiple `export *`s with duplicate names exports a member with that name itself
            if (id == "export=" || !(exportsWithDuplicate && exportsWithDuplicate.length) || hasProperty(symbols, id)) {
              continue
            }
            for (val node of exportsWithDuplicate) {
              diagnostics.add(createDiagnosticForNode(
                node,
                Diagnostics.Module_0_has_already_exported_a_member_named_1_Consider_explicitly_re_exporting_to_resolve_the_ambiguity,
                lookupTable[id].specifierText,
                id
              ))
            }
          }
          extendExportSymbols(symbols, nestedSymbols)
        }
        return symbols
      }
    }

    def getMergedSymbol(symbol: Symbol): Symbol {
      var merged: Symbol
      return symbol && symbol.mergeId && (merged = mergedSymbols[symbol.mergeId]) ? merged : symbol
    }

    def getSymbolOfNode(node: Node): Symbol {
      return getMergedSymbol(node.symbol)
    }

    def getParentOfSymbol(symbol: Symbol): Symbol {
      return getMergedSymbol(symbol.parent)
    }

    def getExportSymbolOfValueSymbolIfExported(symbol: Symbol): Symbol {
      return symbol && (symbol.flags & SymbolFlags.ExportValue) != 0
        ? getMergedSymbol(symbol.exportSymbol)
        : symbol
    }

    def symbolIsValue(symbol: Symbol): Boolean {
      // If it is an instantiated symbol, then it is a value if the symbol it is an
      // instantiation of is a value.
      if (symbol.flags & SymbolFlags.Instantiated) {
        return symbolIsValue(getSymbolLinks(symbol).target)
      }

      // If the symbol has the value flag, it is trivially a value.
      if (symbol.flags & SymbolFlags.Value) {
        return true
      }

      // If it is an alias, then it is a value if the symbol it resolves to is a value.
      if (symbol.flags & SymbolFlags.Alias) {
        return (resolveAlias(symbol).flags & SymbolFlags.Value) != 0
      }

      return false
    }

    def findConstructorDeclaration(node: ClassLikeDeclaration): ConstructorDeclaration {
      val members = node.members
      for (val member of members) {
        if (member.kind == SyntaxKind.Constructor && nodeIsPresent((<ConstructorDeclaration>member).body)) {
          return <ConstructorDeclaration>member
        }
      }
    }

    def createType(flags: TypeFlags): Type {
      val result = new Type(checker, flags)
      result.id = typeCount
      typeCount++
      return result
    }

    def createIntrinsicType(kind: TypeFlags, intrinsicName: String): IntrinsicType {
      val type = <IntrinsicType>createType(kind)
      type.intrinsicName = intrinsicName
      return type
    }

    def createObjectType(kind: TypeFlags, symbol?: Symbol): ObjectType {
      val type = <ObjectType>createType(kind)
      type.symbol = symbol
      return type
    }

    // A reserved member name starts with two underscores, but the third character cannot be an underscore
    // or the @ symbol. A third underscore indicates an escaped form of an identifer that started
    // with at least two underscores. The @ character indicates that the name is denoted by a well known ES
    // Symbol instance.
    def isReservedMemberName(name: String) {
      return name.charCodeAt(0) == CharacterCodes._ &&
        name.charCodeAt(1) == CharacterCodes._ &&
        name.charCodeAt(2) != CharacterCodes._ &&
        name.charCodeAt(2) != CharacterCodes.at
    }

    def getNamedMembers(members: SymbolTable): Symbol[] {
      var result: Symbol[]
      for (val id in members) {
        if (hasProperty(members, id)) {
          if (!isReservedMemberName(id)) {
            if (!result) result = []
            val symbol = members[id]
            if (symbolIsValue(symbol)) {
              result.push(symbol)
            }
          }
        }
      }
      return result || emptyArray
    }

    def setObjectTypeMembers(type: ObjectType, members: SymbolTable, callSignatures: Signature[], constructSignatures: Signature[], stringIndexInfo: IndexInfo, numberIndexInfo: IndexInfo): ResolvedType {
      (<ResolvedType>type).members = members
      (<ResolvedType>type).properties = getNamedMembers(members)
      (<ResolvedType>type).callSignatures = callSignatures
      (<ResolvedType>type).constructSignatures = constructSignatures
      if (stringIndexInfo) (<ResolvedType>type).stringIndexInfo = stringIndexInfo
      if (numberIndexInfo) (<ResolvedType>type).numberIndexInfo = numberIndexInfo
      return <ResolvedType>type
    }

    def createAnonymousType(symbol: Symbol, members: SymbolTable, callSignatures: Signature[], constructSignatures: Signature[], stringIndexInfo: IndexInfo, numberIndexInfo: IndexInfo): ResolvedType {
      return setObjectTypeMembers(createObjectType(TypeFlags.Anonymous, symbol),
        members, callSignatures, constructSignatures, stringIndexInfo, numberIndexInfo)
    }

    def forEachSymbolTableInScope<T>(enclosingDeclaration: Node, callback: (symbolTable: SymbolTable) => T): T {
      var result: T
      for (var location = enclosingDeclaration; location; location = location.parent) {
        // Locals of a source file are not in scope (because they get merged into the global symbol table)
        if (location.locals && !isGlobalSourceFile(location)) {
          if (result = callback(location.locals)) {
            return result
          }
        }
        switch (location.kind) {
          case SyntaxKind.SourceFile:
            if (!isExternalOrCommonJsModule(<SourceFile>location)) {
              break
            }
          case SyntaxKind.ModuleDeclaration:
            if (result = callback(getSymbolOfNode(location).exports)) {
              return result
            }
            break
          case SyntaxKind.ClassDeclaration:
          case SyntaxKind.InterfaceDeclaration:
            if (result = callback(getSymbolOfNode(location).members)) {
              return result
            }
            break
        }
      }

      return callback(globals)
    }

    def getQualifiedLeftMeaning(rightMeaning: SymbolFlags) {
      // If we are looking in value space, the parent meaning is value, other wise it is package
      return rightMeaning == SymbolFlags.Value ? SymbolFlags.Value : SymbolFlags.Namespace
    }

    def getAccessibleSymbolChain(symbol: Symbol, enclosingDeclaration: Node, meaning: SymbolFlags, useOnlyExternalAliasing: Boolean): Symbol[] {
      def getAccessibleSymbolChainFromSymbolTable(symbols: SymbolTable): Symbol[] {
        def canQualifySymbol(symbolFromSymbolTable: Symbol, meaning: SymbolFlags) {
          // If the symbol is equivalent and doesn't need further qualification, this symbol is accessible
          if (!needsQualification(symbolFromSymbolTable, enclosingDeclaration, meaning)) {
            return true
          }

          // If symbol needs qualification, make sure that parent is accessible, if it is then this symbol is accessible too
          val accessibleParent = getAccessibleSymbolChain(symbolFromSymbolTable.parent, enclosingDeclaration, getQualifiedLeftMeaning(meaning), useOnlyExternalAliasing)
          return !!accessibleParent
        }

        def isAccessible(symbolFromSymbolTable: Symbol, resolvedAliasSymbol?: Symbol) {
          if (symbol == (resolvedAliasSymbol || symbolFromSymbolTable)) {
            // if the symbolFromSymbolTable is not external module (it could be if it was determined as ambient external module and would be in globals table)
            // and if symbolFromSymbolTable or alias resolution matches the symbol,
            // check the symbol can be qualified, it is only then this symbol is accessible
            return !forEach(symbolFromSymbolTable.declarations, hasExternalModuleSymbol) &&
              canQualifySymbol(symbolFromSymbolTable, meaning)
          }
        }

        // If symbol is directly available by its name in the symbol table
        if (isAccessible(lookUp(symbols, symbol.name))) {
          return [symbol]
        }

        // Check if symbol is any of the alias
        return forEachValue(symbols, symbolFromSymbolTable => {
          if (symbolFromSymbolTable.flags & SymbolFlags.Alias
            && symbolFromSymbolTable.name != "export="
            && !getDeclarationOfKind(symbolFromSymbolTable, SyntaxKind.ExportSpecifier)) {
            if (!useOnlyExternalAliasing || // We can use any type of alias to get the name
              // Is this external alias, then use it to name
              ts.forEach(symbolFromSymbolTable.declarations, isExternalModuleImportEqualsDeclaration)) {

              val resolvedImportedSymbol = resolveAlias(symbolFromSymbolTable)
              if (isAccessible(symbolFromSymbolTable, resolveAlias(symbolFromSymbolTable))) {
                return [symbolFromSymbolTable]
              }

              // Look in the exported members, if we can find accessibleSymbolChain, symbol is accessible using this chain
              // but only if the symbolFromSymbolTable can be qualified
              val accessibleSymbolsFromExports = resolvedImportedSymbol.exports ? getAccessibleSymbolChainFromSymbolTable(resolvedImportedSymbol.exports) : ()
              if (accessibleSymbolsFromExports && canQualifySymbol(symbolFromSymbolTable, getQualifiedLeftMeaning(meaning))) {
                return [symbolFromSymbolTable].concat(accessibleSymbolsFromExports)
              }
            }
          }
        })
      }

      if (symbol) {
        return forEachSymbolTableInScope(enclosingDeclaration, getAccessibleSymbolChainFromSymbolTable)
      }
    }

    def needsQualification(symbol: Symbol, enclosingDeclaration: Node, meaning: SymbolFlags) {
      var qualify = false
      forEachSymbolTableInScope(enclosingDeclaration, symbolTable => {
        // If symbol of this name is not available in the symbol table we are ok
        if (!hasProperty(symbolTable, symbol.name)) {
          // Continue to the next symbol table
          return false
        }
        // If the symbol with this name is present it should refer to the symbol
        var symbolFromSymbolTable = symbolTable[symbol.name]
        if (symbolFromSymbolTable == symbol) {
          // No need to qualify
          return true
        }

        // Qualify if the symbol from symbol table has same meaning as expected
        symbolFromSymbolTable = (symbolFromSymbolTable.flags & SymbolFlags.Alias && !getDeclarationOfKind(symbolFromSymbolTable, SyntaxKind.ExportSpecifier)) ? resolveAlias(symbolFromSymbolTable) : symbolFromSymbolTable
        if (symbolFromSymbolTable.flags & meaning) {
          qualify = true
          return true
        }

        // Continue to the next symbol table
        return false
      })

      return qualify
    }

    def isSymbolAccessible(symbol: Symbol, enclosingDeclaration: Node, meaning: SymbolFlags): SymbolAccessibilityResult {
      if (symbol && enclosingDeclaration && !(symbol.flags & SymbolFlags.TypeParameter)) {
        val initialSymbol = symbol
        var meaningToLook = meaning
        while (symbol) {
          // Symbol is accessible if it by itself is accessible
          val accessibleSymbolChain = getAccessibleSymbolChain(symbol, enclosingDeclaration, meaningToLook, /*useOnlyExternalAliasing*/ false)
          if (accessibleSymbolChain) {
            val hasAccessibleDeclarations = hasVisibleDeclarations(accessibleSymbolChain[0])
            if (!hasAccessibleDeclarations) {
              return <SymbolAccessibilityResult>{
                accessibility: SymbolAccessibility.NotAccessible,
                errorSymbolName: symbolToString(initialSymbol, enclosingDeclaration, meaning),
                errorModuleName: symbol != initialSymbol ? symbolToString(symbol, enclosingDeclaration, SymbolFlags.Namespace) : (),
              }
            }
            return hasAccessibleDeclarations
          }

          // If we haven't got the accessible symbol, it doesn't mean the symbol is actually inaccessible.
          // It could be a qualified symbol and hence verify the path
          // e.g.:
          // module m {
          //   class c {
          //   }
          // }
          // val x: typeof m.c
          // In the above example when we start with checking if typeof m.c symbol is accessible,
          // we are going to see if c can be accessed in scope directly.
          // But it can't, hence the accessible is going to be (), but that doesn't mean m.c is inaccessible
          // It is accessible if the parent m is accessible because then m.c can be accessed through qualification
          meaningToLook = getQualifiedLeftMeaning(meaning)
          symbol = getParentOfSymbol(symbol)
        }

        // This could be a symbol that is not exported in the external module
        // or it could be a symbol from different external module that is not aliased and hence cannot be named
        val symbolExternalModule = forEach(initialSymbol.declarations, getExternalModuleContainer)
        if (symbolExternalModule) {
          val enclosingExternalModule = getExternalModuleContainer(enclosingDeclaration)
          if (symbolExternalModule != enclosingExternalModule) {
            // name from different external module that is not visible
            return {
              accessibility: SymbolAccessibility.CannotBeNamed,
              errorSymbolName: symbolToString(initialSymbol, enclosingDeclaration, meaning),
              errorModuleName: symbolToString(symbolExternalModule)
            }
          }
        }

        // Just a local name that is not accessible
        return {
          accessibility: SymbolAccessibility.NotAccessible,
          errorSymbolName: symbolToString(initialSymbol, enclosingDeclaration, meaning),
        }
      }

      return { accessibility: SymbolAccessibility.Accessible }

      def getExternalModuleContainer(declaration: Node) {
        for (; declaration; declaration = declaration.parent) {
          if (hasExternalModuleSymbol(declaration)) {
            return getSymbolOfNode(declaration)
          }
        }
      }
    }

    def hasExternalModuleSymbol(declaration: Node) {
      return isAmbientModule(declaration) || (declaration.kind == SyntaxKind.SourceFile && isExternalOrCommonJsModule(<SourceFile>declaration))
    }

    def hasVisibleDeclarations(symbol: Symbol): SymbolVisibilityResult {
      var aliasesToMakeVisible: AnyImportSyntax[]
      if (forEach(symbol.declarations, declaration => !getIsDeclarationVisible(declaration))) {
        return ()
      }
      return { accessibility: SymbolAccessibility.Accessible, aliasesToMakeVisible }

      def getIsDeclarationVisible(declaration: Declaration) {
        if (!isDeclarationVisible(declaration)) {
          // Mark the unexported alias as visible if its parent is visible
          // because these kind of aliases can be used to name types in declaration file

          val anyImportSyntax = getAnyImportSyntax(declaration)
          if (anyImportSyntax &&
            !(anyImportSyntax.flags & NodeFlags.Export) && // import clause without export
            isDeclarationVisible(<Declaration>anyImportSyntax.parent)) {
            getNodeLinks(declaration).isVisible = true
            if (aliasesToMakeVisible) {
              if (!contains(aliasesToMakeVisible, anyImportSyntax)) {
                aliasesToMakeVisible.push(anyImportSyntax)
              }
            }
            else {
              aliasesToMakeVisible = [anyImportSyntax]
            }
            return true
          }

          // Declaration is not visible
          return false
        }

        return true
      }
    }

    def isEntityNameVisible(entityName: EntityName | Expression, enclosingDeclaration: Node): SymbolVisibilityResult {
      // get symbol of the first identifier of the entityName
      var meaning: SymbolFlags
      if (entityName.parent.kind == SyntaxKind.TypeQuery) {
        // Typeof value
        meaning = SymbolFlags.Value | SymbolFlags.ExportValue
      }
      else if (entityName.kind == SyntaxKind.QualifiedName || entityName.kind == SyntaxKind.PropertyAccessExpression ||
        entityName.parent.kind == SyntaxKind.ImportEqualsDeclaration) {
        // Left identifier from type reference or TypeAlias
        // Entity name of the import declaration
        meaning = SymbolFlags.Namespace
      }
      else {
        // Type Reference or TypeAlias entity = Identifier
        meaning = SymbolFlags.Type
      }

      val firstIdentifier = getFirstIdentifier(entityName)
      val symbol = resolveName(enclosingDeclaration, (<Identifier>firstIdentifier).text, meaning, /*nodeNotFoundErrorMessage*/ (), /*nameArg*/ ())

      // Verify if the symbol is accessible
      return (symbol && hasVisibleDeclarations(symbol)) || <SymbolVisibilityResult>{
        accessibility: SymbolAccessibility.NotAccessible,
        errorSymbolName: getTextOfNode(firstIdentifier),
        errorNode: firstIdentifier
      }
    }

    def writeKeyword(writer: SymbolWriter, kind: SyntaxKind) {
      writer.writeKeyword(tokenToString(kind))
    }

    def writePunctuation(writer: SymbolWriter, kind: SyntaxKind) {
      writer.writePunctuation(tokenToString(kind))
    }

    def writeSpace(writer: SymbolWriter) {
      writer.writeSpace(" ")
    }

    def symbolToString(symbol: Symbol, enclosingDeclaration?: Node, meaning?: SymbolFlags): String {
      val writer = getSingleLineStringWriter()
      getSymbolDisplayBuilder().buildSymbolDisplay(symbol, writer, enclosingDeclaration, meaning)
      val result = writer.String()
      releaseStringWriter(writer)

      return result
    }

    def signatureToString(signature: Signature, enclosingDeclaration?: Node, flags?: TypeFormatFlags, kind?: SignatureKind): String {
      val writer = getSingleLineStringWriter()
      getSymbolDisplayBuilder().buildSignatureDisplay(signature, writer, enclosingDeclaration, flags, kind)
      val result = writer.String()
      releaseStringWriter(writer)

      return result
    }

    def typeToString(type: Type, enclosingDeclaration?: Node, flags?: TypeFormatFlags): String {
      val writer = getSingleLineStringWriter()
      getSymbolDisplayBuilder().buildTypeDisplay(type, writer, enclosingDeclaration, flags)
      var result = writer.String()
      releaseStringWriter(writer)

      val maxLength = compilerOptions.noErrorTruncation || flags & TypeFormatFlags.NoTruncation ? () : 100
      if (maxLength && result.length >= maxLength) {
        result = result.substr(0, maxLength - "...".length) + "..."
      }
      return result
    }

    def getTypeAliasForTypeLiteral(type: Type): Symbol {
      if (type.symbol && type.symbol.flags & SymbolFlags.TypeLiteral) {
        var node = type.symbol.declarations[0].parent
        while (node.kind == SyntaxKind.ParenthesizedType) {
          node = node.parent
        }
        if (node.kind == SyntaxKind.TypeAliasDeclaration) {
          return getSymbolOfNode(node)
        }
      }
      return ()
    }

    def isTopLevelInExternalModuleAugmentation(node: Node): Boolean {
      return node && node.parent &&
        node.parent.kind == SyntaxKind.ModuleBlock &&
        isExternalModuleAugmentation(node.parent.parent)
    }

    def getSymbolDisplayBuilder(): SymbolDisplayBuilder {

      def getNameOfSymbol(symbol: Symbol): String {
        if (symbol.declarations && symbol.declarations.length) {
          val declaration = symbol.declarations[0]
          if (declaration.name) {
            return declarationNameToString(declaration.name)
          }
          switch (declaration.kind) {
            case SyntaxKind.ClassExpression:
              return "(Anonymous class)"
            case SyntaxKind.FunctionExpression:
            case SyntaxKind.ArrowFunction:
              return "(Anonymous def)"
          }
        }
        return symbol.name
      }

      /**
       * Writes only the name of the symbol out to the writer. Uses the original source text
       * for the name of the symbol if it is available to match how the user inputted the name.
       */
      def appendSymbolNameOnly(symbol: Symbol, writer: SymbolWriter): Unit {
        writer.writeSymbol(getNameOfSymbol(symbol), symbol)
      }

      /**
       * Enclosing declaration is optional when we don't want to get qualified name in the enclosing declaration scope
       * Meaning needs to be specified if the enclosing declaration is given
       */
      def buildSymbolDisplay(symbol: Symbol, writer: SymbolWriter, enclosingDeclaration?: Node, meaning?: SymbolFlags, flags?: SymbolFormatFlags, typeFlags?: TypeFormatFlags): Unit {
        var parentSymbol: Symbol
        def appendParentTypeArgumentsAndSymbolName(symbol: Symbol): Unit {
          if (parentSymbol) {
            // Write type arguments of instantiated class/trait here
            if (flags & SymbolFormatFlags.WriteTypeParametersOrArguments) {
              if (symbol.flags & SymbolFlags.Instantiated) {
                buildDisplayForTypeArgumentsAndDelimiters(getTypeParametersOfClassOrInterface(parentSymbol),
                  (<TransientSymbol>symbol).mapper, writer, enclosingDeclaration)
              }
              else {
                buildTypeParameterDisplayFromSymbol(parentSymbol, writer, enclosingDeclaration)
              }
            }
            writePunctuation(writer, SyntaxKind.DotToken)
          }
          parentSymbol = symbol
          appendSymbolNameOnly(symbol, writer)
        }

        // val the writer know we just wrote out a symbol.  The declaration emitter writer uses
        // this to determine if an import it has previously seen (and not written out) needs
        // to be written to the file once the walk of the tree is complete.
        //
        // NOTE(cyrusn): This approach feels somewhat unfortunate.  A simple pass over the tree
        // up front (for example, during checking) could determine if we need to emit the imports
        // and we could then access that data during declaration emit.
        writer.trackSymbol(symbol, enclosingDeclaration, meaning)
        def walkSymbol(symbol: Symbol, meaning: SymbolFlags): Unit {
          if (symbol) {
            val accessibleSymbolChain = getAccessibleSymbolChain(symbol, enclosingDeclaration, meaning, !!(flags & SymbolFormatFlags.UseOnlyExternalAliasing))

            if (!accessibleSymbolChain ||
              needsQualification(accessibleSymbolChain[0], enclosingDeclaration, accessibleSymbolChain.length == 1 ? meaning : getQualifiedLeftMeaning(meaning))) {

              // Go up and add our parent.
              walkSymbol(
                getParentOfSymbol(accessibleSymbolChain ? accessibleSymbolChain[0] : symbol),
                getQualifiedLeftMeaning(meaning))
            }

            if (accessibleSymbolChain) {
              for (val accessibleSymbol of accessibleSymbolChain) {
                appendParentTypeArgumentsAndSymbolName(accessibleSymbol)
              }
            }
            else {
              // If we didn't find accessible symbol chain for this symbol, break if this is external module
              if (!parentSymbol && ts.forEach(symbol.declarations, hasExternalModuleSymbol)) {
                return
              }

              // if this is anonymous type break
              if (symbol.flags & SymbolFlags.TypeLiteral || symbol.flags & SymbolFlags.ObjectLiteral) {
                return
              }

              appendParentTypeArgumentsAndSymbolName(symbol)
            }
          }
        }

        // Get qualified name if the symbol is not a type parameter
        // and there is an enclosing declaration or we specifically
        // asked for it
        val isTypeParameter = symbol.flags & SymbolFlags.TypeParameter
        val typeFormatFlag = TypeFormatFlags.UseFullyQualifiedType & typeFlags
        if (!isTypeParameter && (enclosingDeclaration || typeFormatFlag)) {
          walkSymbol(symbol, meaning)
          return
        }

        return appendParentTypeArgumentsAndSymbolName(symbol)
      }

      def buildTypeDisplay(type: Type, writer: SymbolWriter, enclosingDeclaration?: Node, globalFlags?: TypeFormatFlags, symbolStack?: Symbol[]) {
        val globalFlagsToPass = globalFlags & TypeFormatFlags.WriteOwnNameForAnyLike
        var inObjectTypeLiteral = false
        return writeType(type, globalFlags)

        def writeType(type: Type, flags: TypeFormatFlags) {
          // Write ()/null type as any
          if (type.flags & TypeFlags.Intrinsic) {
            if (type.flags & TypeFlags.PredicateType) {
              buildTypePredicateDisplay(writer, (type as PredicateType).predicate)
              buildTypeDisplay((type as PredicateType).predicate.type, writer, enclosingDeclaration, flags, symbolStack)
            }
            else {
              // Special handling for unknown / resolving types, they should show up as any and not unknown or __resolving
              writer.writeKeyword(!(globalFlags & TypeFormatFlags.WriteOwnNameForAnyLike) && isTypeAny(type)
                ? "any"
                : (<IntrinsicType>type).intrinsicName)
            }
          }
          else if (type.flags & TypeFlags.ThisType) {
            if (inObjectTypeLiteral) {
              writer.reportInaccessibleThisError()
            }
            writer.writeKeyword("this")
          }
          else if (type.flags & TypeFlags.Reference) {
            writeTypeReference(<TypeReference>type, flags)
          }
          else if (type.flags & (TypeFlags.Class | TypeFlags.Interface | TypeFlags.Enum | TypeFlags.TypeParameter)) {
            // The specified symbol flags need to be reinterpreted as type flags
            buildSymbolDisplay(type.symbol, writer, enclosingDeclaration, SymbolFlags.Type, SymbolFormatFlags.None, flags)
          }
          else if (type.flags & TypeFlags.Tuple) {
            writeTupleType(<TupleType>type)
          }
          else if (type.flags & TypeFlags.UnionOrIntersection) {
            writeUnionOrIntersectionType(<UnionOrIntersectionType>type, flags)
          }
          else if (type.flags & TypeFlags.Anonymous) {
            writeAnonymousType(<ObjectType>type, flags)
          }
          else if (type.flags & TypeFlags.StringLiteral) {
            writer.writeStringLiteral(`"${escapeString((<StringLiteralType>type).text)}"`)
          }
          else {
            // Should never get here
            // { ... }
            writePunctuation(writer, SyntaxKind.OpenBraceToken)
            writeSpace(writer)
            writePunctuation(writer, SyntaxKind.DotDotDotToken)
            writeSpace(writer)
            writePunctuation(writer, SyntaxKind.CloseBraceToken)
          }
        }

        def writeTypeList(types: Type[], delimiter: SyntaxKind) {
          for (var i = 0; i < types.length; i++) {
            if (i > 0) {
              if (delimiter != SyntaxKind.CommaToken) {
                writeSpace(writer)
              }
              writePunctuation(writer, delimiter)
              writeSpace(writer)
            }
            writeType(types[i], delimiter == SyntaxKind.CommaToken ? TypeFormatFlags.None : TypeFormatFlags.InElementType)
          }
        }

        def writeSymbolTypeReference(symbol: Symbol, typeArguments: Type[], pos: Int, end: Int, flags: TypeFormatFlags) {
          // Unnamed def expressions and arrow functions have reserved names that we don't want to display
          if (symbol.flags & SymbolFlags.Class || !isReservedMemberName(symbol.name)) {
            buildSymbolDisplay(symbol, writer, enclosingDeclaration, SymbolFlags.Type, SymbolFormatFlags.None, flags)
          }
          if (pos < end) {
            writePunctuation(writer, SyntaxKind.LessThanToken)
            writeType(typeArguments[pos], TypeFormatFlags.None)
            pos++
            while (pos < end) {
              writePunctuation(writer, SyntaxKind.CommaToken)
              writeSpace(writer)
              writeType(typeArguments[pos], TypeFormatFlags.None)
              pos++
            }
            writePunctuation(writer, SyntaxKind.GreaterThanToken)
          }
        }

        def writeTypeReference(type: TypeReference, flags: TypeFormatFlags) {
          val typeArguments = type.typeArguments || emptyArray
          if (type.target == globalArrayType && !(flags & TypeFormatFlags.WriteArrayAsGenericType)) {
            writeType(typeArguments[0], TypeFormatFlags.InElementType)
            writePunctuation(writer, SyntaxKind.OpenBracketToken)
            writePunctuation(writer, SyntaxKind.CloseBracketToken)
          }
          else {
            // Write the type reference in the format f<A>.g<B>.C<X, Y> where A and B are type arguments
            // for outer type parameters, and f and g are the respective declaring containers of those
            // type parameters.
            val outerTypeParameters = type.target.outerTypeParameters
            var i = 0
            if (outerTypeParameters) {
              val length = outerTypeParameters.length
              while (i < length) {
                // Find group of type arguments for type parameters with the same declaring container.
                val start = i
                val parent = getParentSymbolOfTypeParameter(outerTypeParameters[i])
                do {
                  i++
                } while (i < length && getParentSymbolOfTypeParameter(outerTypeParameters[i]) == parent)
                // When type parameters are their own type arguments for the whole group (i.e. we have
                // the default outer type arguments), we don't show the group.
                if (!rangeEquals(outerTypeParameters, typeArguments, start, i)) {
                  writeSymbolTypeReference(parent, typeArguments, start, i, flags)
                  writePunctuation(writer, SyntaxKind.DotToken)
                }
              }
            }
            val typeParameterCount = (type.target.typeParameters || emptyArray).length
            writeSymbolTypeReference(type.symbol, typeArguments, i, typeParameterCount, flags)
          }
        }

        def writeTupleType(type: TupleType) {
          writePunctuation(writer, SyntaxKind.OpenBracketToken)
          writeTypeList(type.elementTypes, SyntaxKind.CommaToken)
          writePunctuation(writer, SyntaxKind.CloseBracketToken)
        }

        def writeUnionOrIntersectionType(type: UnionOrIntersectionType, flags: TypeFormatFlags) {
          if (flags & TypeFormatFlags.InElementType) {
            writePunctuation(writer, SyntaxKind.OpenParenToken)
          }
          writeTypeList(type.types, type.flags & TypeFlags.Union ? SyntaxKind.BarToken : SyntaxKind.AmpersandToken)
          if (flags & TypeFormatFlags.InElementType) {
            writePunctuation(writer, SyntaxKind.CloseParenToken)
          }
        }

        def writeAnonymousType(type: ObjectType, flags: TypeFormatFlags) {
          val symbol = type.symbol
          if (symbol) {
            // Always use 'typeof T' for type of class, enum, and module objects
            if (symbol.flags & (SymbolFlags.Class | SymbolFlags.Enum | SymbolFlags.ValueModule)) {
              writeTypeofSymbol(type, flags)
            }
            else if (shouldWriteTypeOfFunctionSymbol()) {
              writeTypeofSymbol(type, flags)
            }
            else if (contains(symbolStack, symbol)) {
              // If type is an anonymous type literal in a type alias declaration, use type alias name
              val typeAlias = getTypeAliasForTypeLiteral(type)
              if (typeAlias) {
                // The specified symbol flags need to be reinterpreted as type flags
                buildSymbolDisplay(typeAlias, writer, enclosingDeclaration, SymbolFlags.Type, SymbolFormatFlags.None, flags)
              }
              else {
                // Recursive usage, use any
                writeKeyword(writer, SyntaxKind.AnyKeyword)
              }
            }
            else {
              // Since instantiations of the same anonymous type have the same symbol, tracking symbols instead
              // of types allows us to catch circular references to instantiations of the same anonymous type
              if (!symbolStack) {
                symbolStack = []
              }
              symbolStack.push(symbol)
              writeLiteralType(type, flags)
              symbolStack.pop()
            }
          }
          else {
            // Anonymous types with no symbol are never circular
            writeLiteralType(type, flags)
          }

          def shouldWriteTypeOfFunctionSymbol() {
            val isStaticMethodSymbol = !!(symbol.flags & SymbolFlags.Method &&  // typeof static method
              forEach(symbol.declarations, declaration => declaration.flags & NodeFlags.Static))
            val isNonLocalFunctionSymbol = !!(symbol.flags & SymbolFlags.Function) &&
              (symbol.parent || // is exported def symbol
                forEach(symbol.declarations, declaration =>
                  declaration.parent.kind == SyntaxKind.SourceFile || declaration.parent.kind == SyntaxKind.ModuleBlock))
            if (isStaticMethodSymbol || isNonLocalFunctionSymbol) {
              // typeof is allowed only for static/non local functions
              return !!(flags & TypeFormatFlags.UseTypeOfFunction) || // use typeof if format flags specify it
                (contains(symbolStack, symbol)); // it is type of the symbol uses itself recursively
            }
          }
        }

        def writeTypeofSymbol(type: ObjectType, typeFormatFlags?: TypeFormatFlags) {
          writeKeyword(writer, SyntaxKind.TypeOfKeyword)
          writeSpace(writer)
          buildSymbolDisplay(type.symbol, writer, enclosingDeclaration, SymbolFlags.Value, SymbolFormatFlags.None, typeFormatFlags)
        }

        def writeIndexSignature(info: IndexInfo, keyword: SyntaxKind) {
          if (info) {
            if (info.isReadonly) {
              writeKeyword(writer, SyntaxKind.ReadonlyKeyword)
              writeSpace(writer)
            }
            writePunctuation(writer, SyntaxKind.OpenBracketToken)
            writer.writeParameter(info.declaration ? declarationNameToString(info.declaration.parameters[0].name) : "x")
            writePunctuation(writer, SyntaxKind.ColonToken)
            writeSpace(writer)
            writeKeyword(writer, keyword)
            writePunctuation(writer, SyntaxKind.CloseBracketToken)
            writePunctuation(writer, SyntaxKind.ColonToken)
            writeSpace(writer)
            writeType(info.type, TypeFormatFlags.None)
            writePunctuation(writer, SyntaxKind.SemicolonToken)
            writer.writeLine()
          }
        }

        def writePropertyWithModifiers(prop: Symbol) {
          if (isReadonlySymbol(prop)) {
            writeKeyword(writer, SyntaxKind.ReadonlyKeyword)
            writeSpace(writer)
          }
          buildSymbolDisplay(prop, writer)
          if (prop.flags & SymbolFlags.Optional) {
            writePunctuation(writer, SyntaxKind.QuestionToken)
          }
        }

        def writeLiteralType(type: ObjectType, flags: TypeFormatFlags) {
          val resolved = resolveStructuredTypeMembers(type)
          if (!resolved.properties.length && !resolved.stringIndexInfo && !resolved.numberIndexInfo) {
            if (!resolved.callSignatures.length && !resolved.constructSignatures.length) {
              writePunctuation(writer, SyntaxKind.OpenBraceToken)
              writePunctuation(writer, SyntaxKind.CloseBraceToken)
              return
            }

            if (resolved.callSignatures.length == 1 && !resolved.constructSignatures.length) {
              if (flags & TypeFormatFlags.InElementType) {
                writePunctuation(writer, SyntaxKind.OpenParenToken)
              }
              buildSignatureDisplay(resolved.callSignatures[0], writer, enclosingDeclaration, globalFlagsToPass | TypeFormatFlags.WriteArrowStyleSignature, /*kind*/ (), symbolStack)
              if (flags & TypeFormatFlags.InElementType) {
                writePunctuation(writer, SyntaxKind.CloseParenToken)
              }
              return
            }
            if (resolved.constructSignatures.length == 1 && !resolved.callSignatures.length) {
              if (flags & TypeFormatFlags.InElementType) {
                writePunctuation(writer, SyntaxKind.OpenParenToken)
              }
              writeKeyword(writer, SyntaxKind.NewKeyword)
              writeSpace(writer)
              buildSignatureDisplay(resolved.constructSignatures[0], writer, enclosingDeclaration, globalFlagsToPass | TypeFormatFlags.WriteArrowStyleSignature, /*kind*/ (), symbolStack)
              if (flags & TypeFormatFlags.InElementType) {
                writePunctuation(writer, SyntaxKind.CloseParenToken)
              }
              return
            }
          }

          val saveInObjectTypeLiteral = inObjectTypeLiteral
          inObjectTypeLiteral = true
          writePunctuation(writer, SyntaxKind.OpenBraceToken)
          writer.writeLine()
          writer.increaseIndent()
          for (val signature of resolved.callSignatures) {
            buildSignatureDisplay(signature, writer, enclosingDeclaration, globalFlagsToPass, /*kind*/ (), symbolStack)
            writePunctuation(writer, SyntaxKind.SemicolonToken)
            writer.writeLine()
          }
          for (val signature of resolved.constructSignatures) {
            buildSignatureDisplay(signature, writer, enclosingDeclaration, globalFlagsToPass, SignatureKind.Construct, symbolStack)
            writePunctuation(writer, SyntaxKind.SemicolonToken)
            writer.writeLine()
          }
          writeIndexSignature(resolved.stringIndexInfo, SyntaxKind.StringKeyword)
          writeIndexSignature(resolved.numberIndexInfo, SyntaxKind.NumberKeyword)
          for (val p of resolved.properties) {
            val t = getTypeOfSymbol(p)
            if (p.flags & (SymbolFlags.Function | SymbolFlags.Method) && !getPropertiesOfObjectType(t).length) {
              val signatures = getSignaturesOfType(t, SignatureKind.Call)
              for (val signature of signatures) {
                writePropertyWithModifiers(p)
                buildSignatureDisplay(signature, writer, enclosingDeclaration, globalFlagsToPass, /*kind*/ (), symbolStack)
                writePunctuation(writer, SyntaxKind.SemicolonToken)
                writer.writeLine()
              }
            }
            else {
              writePropertyWithModifiers(p)
              writePunctuation(writer, SyntaxKind.ColonToken)
              writeSpace(writer)
              writeType(t, TypeFormatFlags.None)
              writePunctuation(writer, SyntaxKind.SemicolonToken)
              writer.writeLine()
            }
          }
          writer.decreaseIndent()
          writePunctuation(writer, SyntaxKind.CloseBraceToken)
          inObjectTypeLiteral = saveInObjectTypeLiteral
        }
      }

      def buildTypeParameterDisplayFromSymbol(symbol: Symbol, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags) {
        val targetSymbol = getTargetSymbol(symbol)
        if (targetSymbol.flags & SymbolFlags.Class || targetSymbol.flags & SymbolFlags.Interface || targetSymbol.flags & SymbolFlags.TypeAlias) {
          buildDisplayForTypeParametersAndDelimiters(getLocalTypeParametersOfClassOrInterfaceOrTypeAlias(symbol), writer, enclosingDeclaration, flags)
        }
      }

      def buildTypeParameterDisplay(tp: TypeParameter, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags, symbolStack?: Symbol[]) {
        appendSymbolNameOnly(tp.symbol, writer)
        val constraint = getConstraintOfTypeParameter(tp)
        if (constraint) {
          writeSpace(writer)
          writeKeyword(writer, SyntaxKind.ExtendsKeyword)
          writeSpace(writer)
          buildTypeDisplay(constraint, writer, enclosingDeclaration, flags, symbolStack)
        }
      }

      def buildParameterDisplay(p: Symbol, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags, symbolStack?: Symbol[]) {
        val parameterNode = <ParameterDeclaration>p.valueDeclaration
        if (isRestParameter(parameterNode)) {
          writePunctuation(writer, SyntaxKind.DotDotDotToken)
        }
        appendSymbolNameOnly(p, writer)
        if (isOptionalParameter(parameterNode)) {
          writePunctuation(writer, SyntaxKind.QuestionToken)
        }
        writePunctuation(writer, SyntaxKind.ColonToken)
        writeSpace(writer)

        buildTypeDisplay(getTypeOfSymbol(p), writer, enclosingDeclaration, flags, symbolStack)
      }

      def buildDisplayForTypeParametersAndDelimiters(typeParameters: TypeParameter[], writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags, symbolStack?: Symbol[]) {
        if (typeParameters && typeParameters.length) {
          writePunctuation(writer, SyntaxKind.LessThanToken)
          for (var i = 0; i < typeParameters.length; i++) {
            if (i > 0) {
              writePunctuation(writer, SyntaxKind.CommaToken)
              writeSpace(writer)
            }
            buildTypeParameterDisplay(typeParameters[i], writer, enclosingDeclaration, flags, symbolStack)
          }
          writePunctuation(writer, SyntaxKind.GreaterThanToken)
        }
      }

      def buildDisplayForTypeArgumentsAndDelimiters(typeParameters: TypeParameter[], mapper: TypeMapper, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags, symbolStack?: Symbol[]) {
        if (typeParameters && typeParameters.length) {
          writePunctuation(writer, SyntaxKind.LessThanToken)
          for (var i = 0; i < typeParameters.length; i++) {
            if (i > 0) {
              writePunctuation(writer, SyntaxKind.CommaToken)
              writeSpace(writer)
            }
            buildTypeDisplay(mapper(typeParameters[i]), writer, enclosingDeclaration, TypeFormatFlags.None)
          }
          writePunctuation(writer, SyntaxKind.GreaterThanToken)
        }
      }

      def buildDisplayForParametersAndDelimiters(parameters: Symbol[], writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags, symbolStack?: Symbol[]) {
        writePunctuation(writer, SyntaxKind.OpenParenToken)
        for (var i = 0; i < parameters.length; i++) {
          if (i > 0) {
            writePunctuation(writer, SyntaxKind.CommaToken)
            writeSpace(writer)
          }
          buildParameterDisplay(parameters[i], writer, enclosingDeclaration, flags, symbolStack)
        }
        writePunctuation(writer, SyntaxKind.CloseParenToken)
      }

      def buildTypePredicateDisplay(writer: SymbolWriter, predicate: TypePredicate) {
        if (isIdentifierTypePredicate(predicate)) {
          writer.writeParameter(predicate.parameterName)
        }
        else {
          writeKeyword(writer, SyntaxKind.ThisKeyword)
        }
        writeSpace(writer)
        writeKeyword(writer, SyntaxKind.IsKeyword)
        writeSpace(writer)
      }

      def buildReturnTypeDisplay(signature: Signature, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags, symbolStack?: Symbol[]) {
        if (flags & TypeFormatFlags.WriteArrowStyleSignature) {
          writeSpace(writer)
          writePunctuation(writer, SyntaxKind.EqualsGreaterThanToken)
        }
        else {
          writePunctuation(writer, SyntaxKind.ColonToken)
        }
        writeSpace(writer)

        val returnType = getReturnTypeOfSignature(signature)
        buildTypeDisplay(returnType, writer, enclosingDeclaration, flags, symbolStack)
      }

      def buildSignatureDisplay(signature: Signature, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags, kind?: SignatureKind, symbolStack?: Symbol[]) {
        if (kind == SignatureKind.Construct) {
          writeKeyword(writer, SyntaxKind.NewKeyword)
          writeSpace(writer)
        }

        if (signature.target && (flags & TypeFormatFlags.WriteTypeArgumentsOfSignature)) {
          // Instantiated signature, write type arguments instead
          // This is achieved by passing in the mapper separately
          buildDisplayForTypeArgumentsAndDelimiters(signature.target.typeParameters, signature.mapper, writer, enclosingDeclaration)
        }
        else {
          buildDisplayForTypeParametersAndDelimiters(signature.typeParameters, writer, enclosingDeclaration, flags, symbolStack)
        }

        buildDisplayForParametersAndDelimiters(signature.parameters, writer, enclosingDeclaration, flags, symbolStack)
        buildReturnTypeDisplay(signature, writer, enclosingDeclaration, flags, symbolStack)
      }

      return _displayBuilder || (_displayBuilder = {
        buildSymbolDisplay,
        buildTypeDisplay,
        buildTypeParameterDisplay,
        buildParameterDisplay,
        buildDisplayForParametersAndDelimiters,
        buildDisplayForTypeParametersAndDelimiters,
        buildTypeParameterDisplayFromSymbol,
        buildSignatureDisplay,
        buildReturnTypeDisplay
      })
    }

    def isDeclarationVisible(node: Declaration): Boolean {
      if (node) {
        val links = getNodeLinks(node)
        if (links.isVisible == ()) {
          links.isVisible = !!determineIfDeclarationIsVisible()
        }
        return links.isVisible
      }

      return false

      def determineIfDeclarationIsVisible() {
        switch (node.kind) {
          case SyntaxKind.BindingElement:
            return isDeclarationVisible(<Declaration>node.parent.parent)
          case SyntaxKind.VariableDeclaration:
            if (isBindingPattern(node.name) &&
              !(<BindingPattern>node.name).elements.length) {
              // If the binding pattern is empty, this variable declaration is not visible
              return false
            }
          // Otherwise fall through
          case SyntaxKind.ModuleDeclaration:
          case SyntaxKind.ClassDeclaration:
          case SyntaxKind.InterfaceDeclaration:
          case SyntaxKind.TypeAliasDeclaration:
          case SyntaxKind.FunctionDeclaration:
          case SyntaxKind.EnumDeclaration:
          case SyntaxKind.ImportEqualsDeclaration:
            // external module augmentation is always visible
            if (isExternalModuleAugmentation(node)) {
              return true
            }
            val parent = getDeclarationContainer(node)
            // If the node is not exported or it is not ambient module element (except import declaration)
            if (!(getCombinedNodeFlags(node) & NodeFlags.Export) &&
              !(node.kind != SyntaxKind.ImportEqualsDeclaration && parent.kind != SyntaxKind.SourceFile && isInAmbientContext(parent))) {
              return isGlobalSourceFile(parent)
            }
            // Exported members/ambient module elements (exception import declaration) are visible if parent is visible
            return isDeclarationVisible(<Declaration>parent)

          case SyntaxKind.PropertyDeclaration:
          case SyntaxKind.PropertySignature:
          case SyntaxKind.GetAccessor:
          case SyntaxKind.SetAccessor:
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.MethodSignature:
            if (node.flags & (NodeFlags.Private | NodeFlags.Protected)) {
              // Private/protected properties/methods are not visible
              return false
            }
          // Public properties/methods are visible if its parents are visible, so val it fall into next case statement

          case SyntaxKind.Constructor:
          case SyntaxKind.ConstructSignature:
          case SyntaxKind.CallSignature:
          case SyntaxKind.IndexSignature:
          case SyntaxKind.Parameter:
          case SyntaxKind.ModuleBlock:
          case SyntaxKind.FunctionType:
          case SyntaxKind.ConstructorType:
          case SyntaxKind.TypeLiteral:
          case SyntaxKind.TypeReference:
          case SyntaxKind.ArrayType:
          case SyntaxKind.TupleType:
          case SyntaxKind.UnionType:
          case SyntaxKind.IntersectionType:
          case SyntaxKind.ParenthesizedType:
            return isDeclarationVisible(<Declaration>node.parent)

          // Default binding, import specifier and package import is visible
          // only on demand so by default it is not visible
          case SyntaxKind.ImportClause:
          case SyntaxKind.NamespaceImport:
          case SyntaxKind.ImportSpecifier:
            return false

          // Type parameters are always visible
          case SyntaxKind.TypeParameter:
          // Source file is always visible
          case SyntaxKind.SourceFile:
            return true

          // Export assignments do not create name bindings outside the module
          case SyntaxKind.ExportAssignment:
            return false

          default:
            Debug.fail("isDeclarationVisible unknown: SyntaxKind: " + node.kind)
        }
      }
    }

    def collectLinkedAliases(node: Identifier): Node[] {
      var exportSymbol: Symbol
      if (node.parent && node.parent.kind == SyntaxKind.ExportAssignment) {
        exportSymbol = resolveName(node.parent, node.text, SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace | SymbolFlags.Alias, Diagnostics.Cannot_find_name_0, node)
      }
      else if (node.parent.kind == SyntaxKind.ExportSpecifier) {
        val exportSpecifier = <ExportSpecifier>node.parent
        exportSymbol = (<ExportDeclaration>exportSpecifier.parent.parent).moduleSpecifier ?
          getExternalModuleMember(<ExportDeclaration>exportSpecifier.parent.parent, exportSpecifier) :
          resolveEntityName(exportSpecifier.propertyName || exportSpecifier.name, SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace | SymbolFlags.Alias)
      }
      val result: Node[] = []
      if (exportSymbol) {
        buildVisibleNodeList(exportSymbol.declarations)
      }
      return result

      def buildVisibleNodeList(declarations: Declaration[]) {
        forEach(declarations, declaration => {
          getNodeLinks(declaration).isVisible = true
          val resultNode = getAnyImportSyntax(declaration) || declaration
          if (!contains(result, resultNode)) {
            result.push(resultNode)
          }

          if (isInternalModuleImportEqualsDeclaration(declaration)) {
            // Add the referenced top container visible
            val internalModuleReference = <Identifier | QualifiedName>(<ImportEqualsDeclaration>declaration).moduleReference
            val firstIdentifier = getFirstIdentifier(internalModuleReference)
            val importSymbol = resolveName(declaration, firstIdentifier.text, SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace,
              Diagnostics.Cannot_find_name_0, firstIdentifier)
            if (importSymbol) {
              buildVisibleNodeList(importSymbol.declarations)
            }
          }
        })
      }
    }

    /**
     * Push an entry on the type resolution stack. If an entry with the given target and the given property name
     * is already on the stack, and no entries in between already have a type, then a circularity has occurred.
     * In this case, the result values of the existing entry and all entries pushed after it are changed to false,
     * and the value false is returned. Otherwise, the new entry is just pushed onto the stack, and true is returned.
     * In order to see if the same query has already been done before, the target object and the propertyName both
     * must match the one passed in.
     *
     * @param target The symbol, type, or signature whose type is being queried
     * @param propertyName The property name that should be used to query the target for its type
     */
    def pushTypeResolution(target: TypeSystemEntity, propertyName: TypeSystemPropertyName): Boolean {
      val resolutionCycleStartIndex = findResolutionCycleStartIndex(target, propertyName)
      if (resolutionCycleStartIndex >= 0) {
        // A cycle was found
        val { length } = resolutionTargets
        for (var i = resolutionCycleStartIndex; i < length; i++) {
          resolutionResults[i] = false
        }
        return false
      }
      resolutionTargets.push(target)
      resolutionResults.push(/*items*/ true)
      resolutionPropertyNames.push(propertyName)
      return true
    }

    def findResolutionCycleStartIndex(target: TypeSystemEntity, propertyName: TypeSystemPropertyName): Int {
      for (var i = resolutionTargets.length - 1; i >= 0; i--) {
        if (hasType(resolutionTargets[i], resolutionPropertyNames[i])) {
          return -1
        }
        if (resolutionTargets[i] == target && resolutionPropertyNames[i] == propertyName) {
          return i
        }
      }

      return -1
    }

    def hasType(target: TypeSystemEntity, propertyName: TypeSystemPropertyName): Type {
      if (propertyName == TypeSystemPropertyName.Type) {
        return getSymbolLinks(<Symbol>target).type
      }
      if (propertyName == TypeSystemPropertyName.DeclaredType) {
        return getSymbolLinks(<Symbol>target).declaredType
      }
      if (propertyName == TypeSystemPropertyName.ResolvedBaseConstructorType) {
        Debug.assert(!!((<Type>target).flags & TypeFlags.Class))
        return (<InterfaceType>target).resolvedBaseConstructorType
      }
      if (propertyName == TypeSystemPropertyName.ResolvedReturnType) {
        return (<Signature>target).resolvedReturnType
      }

      Debug.fail("Unhandled TypeSystemPropertyName " + propertyName)
    }

    // Pop an entry from the type resolution stack and return its associated result value. The result value will
    // be true if no circularities were detected, or false if a circularity was found.
    def popTypeResolution(): Boolean {
      resolutionTargets.pop()
      resolutionPropertyNames.pop()
      return resolutionResults.pop()
    }

    def getDeclarationContainer(node: Node): Node {
      node = getRootDeclaration(node)
      while (node) {
        switch (node.kind) {
          case SyntaxKind.VariableDeclaration:
          case SyntaxKind.VariableDeclarationList:
          case SyntaxKind.ImportSpecifier:
          case SyntaxKind.NamedImports:
          case SyntaxKind.NamespaceImport:
          case SyntaxKind.ImportClause:
            node = node.parent
            break

          default:
            return node.parent
        }
      }
    }

    def getTypeOfPrototypeProperty(prototype: Symbol): Type {
      // TypeScript 1.0 spec (April 2014): 8.4
      // Every class automatically contains a static property member named 'prototype',
      // the type of which is an instantiation of the class type with type Any supplied as a type argument for each type parameter.
      // It is an error to explicitly declare a static property member with the name 'prototype'.
      val classType = <InterfaceType>getDeclaredTypeOfSymbol(getParentOfSymbol(prototype))
      return classType.typeParameters ? createTypeReference(<GenericType>classType, map(classType.typeParameters, _ => anyType)) : classType
    }

    // Return the type of the given property in the given type, or () if no such property exists
    def getTypeOfPropertyOfType(type: Type, name: String): Type {
      val prop = getPropertyOfType(type, name)
      return prop ? getTypeOfSymbol(prop) : ()
    }

    def isTypeAny(type: Type) {
      return type && (type.flags & TypeFlags.Any) != 0
    }

    // Return the type of a binding element parent. We check SymbolLinks first to see if a type has been
    // assigned by contextual typing.
    def getTypeForBindingElementParent(node: VariableLikeDeclaration) {
      val symbol = getSymbolOfNode(node)
      return symbol && getSymbolLinks(symbol).type || getTypeForVariableLikeDeclaration(node)
    }

    def getTextOfPropertyName(name: PropertyName): String {
      switch (name.kind) {
        case SyntaxKind.Identifier:
          return (<Identifier>name).text
        case SyntaxKind.StringLiteral:
        case SyntaxKind.NumericLiteral:
          return (<LiteralExpression>name).text
        case SyntaxKind.ComputedPropertyName:
          if (isStringOrNumericLiteral((<ComputedPropertyName>name).expression.kind)) {
            return (<LiteralExpression>(<ComputedPropertyName>name).expression).text
          }
      }

      return ()
    }

    def isComputedNonLiteralName(name: PropertyName): Boolean {
      return name.kind == SyntaxKind.ComputedPropertyName && !isStringOrNumericLiteral((<ComputedPropertyName>name).expression.kind)
    }

    // Return the inferred type for a binding element
    def getTypeForBindingElement(declaration: BindingElement): Type {
      val pattern = <BindingPattern>declaration.parent
      val parentType = getTypeForBindingElementParent(<VariableLikeDeclaration>pattern.parent)
      // If parent has the unknown (error) type, then so does this binding element
      if (parentType == unknownType) {
        return unknownType
      }
      // If no type was specified or inferred for parent, or if the specified or inferred type is any,
      // infer from the initializer of the binding element if one is present. Otherwise, go with the
      // () or any type of the parent.
      if (!parentType || isTypeAny(parentType)) {
        if (declaration.initializer) {
          return checkExpressionCached(declaration.initializer)
        }
        return parentType
      }

      var type: Type
      if (pattern.kind == SyntaxKind.ObjectBindingPattern) {
        // Use explicitly specified property name ({ p: xxx } form), or otherwise the implied name ({ p } form)
        val name = declaration.propertyName || <Identifier>declaration.name
        if (isComputedNonLiteralName(name)) {
          // computed properties with non-literal names are treated as 'any'
          return anyType
        }

        // Use type of the specified property, or otherwise, for a numeric name, the type of the numeric index signature,
        // or otherwise the type of the String index signature.
        val text = getTextOfPropertyName(name)

        type = getTypeOfPropertyOfType(parentType, text) ||
          isNumericLiteralName(text) && getIndexTypeOfType(parentType, IndexKind.Number) ||
          getIndexTypeOfType(parentType, IndexKind.String)
        if (!type) {
          error(name, Diagnostics.Type_0_has_no_property_1_and_no_string_index_signature, typeToString(parentType), declarationNameToString(name))
          return unknownType
        }
      }
      else {
        // This elementType will be used if the specific property corresponding to this index is not
        // present (aka the tuple element property). This call also checks that the parentType is in
        // fact an iterable or array (depending on target language).
        val elementType = checkIteratedTypeOrElementType(parentType, pattern, /*allowStringInput*/ false)
        if (!declaration.dotDotDotToken) {
          // Use specific property type when parent is a tuple or numeric index type when parent is an array
          val propName = "" + indexOf(pattern.elements, declaration)
          type = isTupleLikeType(parentType)
            ? getTypeOfPropertyOfType(parentType, propName)
            : elementType
          if (!type) {
            if (isTupleType(parentType)) {
              error(declaration, Diagnostics.Tuple_type_0_with_length_1_cannot_be_assigned_to_tuple_with_length_2, typeToString(parentType), (<TupleType>parentType).elementTypes.length, pattern.elements.length)
            }
            else {
              error(declaration, Diagnostics.Type_0_has_no_property_1, typeToString(parentType), propName)
            }
            return unknownType
          }
        }
        else {
          // Rest element has an array type with the same element type as the parent type
          type = createArrayType(elementType)
        }
      }
      return type
    }

    def getTypeForVariableLikeDeclarationFromJSDocComment(declaration: VariableLikeDeclaration) {
      val jsDocType = getJSDocTypeForVariableLikeDeclarationFromJSDocComment(declaration)
      if (jsDocType) {
        return getTypeFromTypeNode(jsDocType)
      }
    }

    def getJSDocTypeForVariableLikeDeclarationFromJSDocComment(declaration: VariableLikeDeclaration): JSDocType {
      // First, see if this node has an @type annotation on it directly.
      val typeTag = getJSDocTypeTag(declaration)
      if (typeTag && typeTag.typeExpression) {
        return typeTag.typeExpression.type
      }

      if (declaration.kind == SyntaxKind.VariableDeclaration &&
        declaration.parent.kind == SyntaxKind.VariableDeclarationList &&
        declaration.parent.parent.kind == SyntaxKind.VariableStatement) {

        // @type annotation might have been on the variable statement, try that instead.
        val annotation = getJSDocTypeTag(declaration.parent.parent)
        if (annotation && annotation.typeExpression) {
          return annotation.typeExpression.type
        }
      }
      else if (declaration.kind == SyntaxKind.Parameter) {
        // If it's a parameter, see if the parent has a jsdoc comment with an @param
        // annotation.
        val paramTag = getCorrespondingJSDocParameterTag(<ParameterDeclaration>declaration)
        if (paramTag && paramTag.typeExpression) {
          return paramTag.typeExpression.type
        }
      }

      return ()
    }

    // Return the inferred type for a variable, parameter, or property declaration
    def getTypeForVariableLikeDeclaration(declaration: VariableLikeDeclaration): Type {
      if (declaration.flags & NodeFlags.JavaScriptFile) {
        // If this is a variable in a JavaScript file, then use the JSDoc type (if it has
        // one as its type), otherwise fallback to the below standard TS codepaths to
        // try to figure it out.
        val type = getTypeForVariableLikeDeclarationFromJSDocComment(declaration)
        if (type && type != unknownType) {
          return type
        }
      }

      // A variable declared in a for..in statement is always of type String
      if (declaration.parent.parent.kind == SyntaxKind.ForInStatement) {
        return stringType
      }

      if (declaration.parent.parent.kind == SyntaxKind.ForOfStatement) {
        // checkRightHandSideOfForOf will return () if the for-of expression type was
        // missing properties/signatures required to get its iteratedType (like
        // [Symbol.iterator] or next). This may be because we accessed properties from anyType,
        // or it may have led to an error inside getElementTypeOfIterable.
        return checkRightHandSideOfForOf((<ForOfStatement>declaration.parent.parent).expression) || anyType
      }

      if (isBindingPattern(declaration.parent)) {
        return getTypeForBindingElement(<BindingElement>declaration)
      }

      // Use type from type annotation if one is present
      if (declaration.type) {
        return getTypeFromTypeNode(declaration.type)
      }

      if (declaration.kind == SyntaxKind.Parameter) {
        val func = <FunctionLikeDeclaration>declaration.parent
        // For a parameter of a set accessor, use the type of the get accessor if one is present
        if (func.kind == SyntaxKind.SetAccessor && !hasDynamicName(func)) {
          val getter = <AccessorDeclaration>getDeclarationOfKind(declaration.parent.symbol, SyntaxKind.GetAccessor)
          if (getter) {
            return getReturnTypeOfSignature(getSignatureFromDeclaration(getter))
          }
        }
        // Use contextual parameter type if one is available
        val type = getContextuallyTypedParameterType(<ParameterDeclaration>declaration)
        if (type) {
          return type
        }
      }

      // Use the type of the initializer expression if one is present
      if (declaration.initializer) {
        return checkExpressionCached(declaration.initializer)
      }

      // If it is a short-hand property assignment, use the type of the identifier
      if (declaration.kind == SyntaxKind.ShorthandPropertyAssignment) {
        return checkIdentifier(<Identifier>declaration.name)
      }

      // If the declaration specifies a binding pattern, use the type implied by the binding pattern
      if (isBindingPattern(declaration.name)) {
        return getTypeFromBindingPattern(<BindingPattern>declaration.name, /*includePatternInType*/ false)
      }

      // No type specified and nothing can be inferred
      return ()
    }

    // Return the type implied by a binding pattern element. This is the type of the initializer of the element if
    // one is present. Otherwise, if the element is itself a binding pattern, it is the type implied by the binding
    // pattern. Otherwise, it is the type any.
    def getTypeFromBindingElement(element: BindingElement, includePatternInType?: Boolean): Type {
      if (element.initializer) {
        return getWidenedType(checkExpressionCached(element.initializer))
      }
      if (isBindingPattern(element.name)) {
        return getTypeFromBindingPattern(<BindingPattern>element.name, includePatternInType)
      }
      return anyType
    }

    // Return the type implied by an object binding pattern
    def getTypeFromObjectBindingPattern(pattern: BindingPattern, includePatternInType: Boolean): Type {
      val members: SymbolTable = {}
      var hasComputedProperties = false
      forEach(pattern.elements, e => {
        val name = e.propertyName || <Identifier>e.name
        if (isComputedNonLiteralName(name)) {
          // do not include computed properties in the implied type
          hasComputedProperties = true
          return
        }

        val text = getTextOfPropertyName(name)
        val flags = SymbolFlags.Property | SymbolFlags.Transient | (e.initializer ? SymbolFlags.Optional : 0)
        val symbol = <TransientSymbol>createSymbol(flags, text)
        symbol.type = getTypeFromBindingElement(e, includePatternInType)
        symbol.bindingElement = e
        members[symbol.name] = symbol
      })
      val result = createAnonymousType((), members, emptyArray, emptyArray, (), ())
      if (includePatternInType) {
        result.pattern = pattern
      }
      if (hasComputedProperties) {
        result.flags |= TypeFlags.ObjectLiteralPatternWithComputedProperties
      }
      return result
    }

    // Return the type implied by an array binding pattern
    def getTypeFromArrayBindingPattern(pattern: BindingPattern, includePatternInType: Boolean): Type {
      val elements = pattern.elements
      if (elements.length == 0 || elements[elements.length - 1].dotDotDotToken) {
        return languageVersion >= ScriptTarget.ES6 ? createIterableType(anyType) : anyArrayType
      }
      // If the pattern has at least one element, and no rest element, then it should imply a tuple type.
      val elementTypes = map(elements, e => e.kind == SyntaxKind.OmittedExpression ? anyType : getTypeFromBindingElement(e, includePatternInType))
      if (includePatternInType) {
        val result = createNewTupleType(elementTypes)
        result.pattern = pattern
        return result
      }
      return createTupleType(elementTypes)
    }

    // Return the type implied by a binding pattern. This is the type implied purely by the binding pattern itself
    // and without regard to its context (i.e. without regard any type annotation or initializer associated with the
    // declaration in which the binding pattern is contained). For example, the implied type of [x, y] is [any, any]
    // and the implied type of { x, y: z = 1 } is { x: any; y: Int; }. The type implied by a binding pattern is
    // used as the contextual type of an initializer associated with the binding pattern. Also, for a destructuring
    // parameter with no type annotation or initializer, the type implied by the binding pattern becomes the type of
    // the parameter.
    def getTypeFromBindingPattern(pattern: BindingPattern, includePatternInType?: Boolean): Type {
      return pattern.kind == SyntaxKind.ObjectBindingPattern
        ? getTypeFromObjectBindingPattern(pattern, includePatternInType)
        : getTypeFromArrayBindingPattern(pattern, includePatternInType)
    }

    // Return the type associated with a variable, parameter, or property declaration. In the simple case this is the type
    // specified in a type annotation or inferred from an initializer. However, in the case of a destructuring declaration it
    // is a bit more involved. For example:
    //
    //   var [x, s = ""] = [1, "one"]
    //
    // Here, the array literal [1, "one"] is contextually typed by the type [any, String], which is the implied type of the
    // binding pattern [x, s = ""]. Because the contextual type is a tuple type, the resulting type of [1, "one"] is the
    // tuple type [Int, String]. Thus, the type inferred for 'x' is Int and the type inferred for 's' is String.
    def getWidenedTypeForVariableLikeDeclaration(declaration: VariableLikeDeclaration, reportErrors?: Boolean): Type {
      var type = getTypeForVariableLikeDeclaration(declaration)
      if (type) {
        if (reportErrors) {
          reportErrorsFromWidening(declaration, type)
        }
        // During a normal type check we'll never get to here with a property assignment (the check of the containing
        // object literal uses a different path). We exclude widening only so that language services and type verification
        // tools see the actual type.
        if (declaration.kind == SyntaxKind.PropertyAssignment) {
          return type
        }
        if (type.flags & TypeFlags.PredicateType && (declaration.kind == SyntaxKind.PropertyDeclaration || declaration.kind == SyntaxKind.PropertySignature)) {
          return type
        }
        return getWidenedType(type)
      }

      // Rest parameters default to type any[], other parameters default to type any
      type = declaration.dotDotDotToken ? anyArrayType : anyType

      // Report implicit any errors unless this is a private property within an ambient declaration
      if (reportErrors && compilerOptions.noImplicitAny) {
        val root = getRootDeclaration(declaration)
        if (!isPrivateWithinAmbient(root) && !(root.kind == SyntaxKind.Parameter && isPrivateWithinAmbient(root.parent))) {
          reportImplicitAnyError(declaration, type)
        }
      }
      return type
    }

    def getTypeOfVariableOrParameterOrProperty(symbol: Symbol): Type {
      val links = getSymbolLinks(symbol)
      if (!links.type) {
        // Handle prototype property
        if (symbol.flags & SymbolFlags.Prototype) {
          return links.type = getTypeOfPrototypeProperty(symbol)
        }
        // Handle catch clause variables
        val declaration = symbol.valueDeclaration
        if (declaration.parent.kind == SyntaxKind.CatchClause) {
          return links.type = anyType
        }
        // Handle default expressions
        if (declaration.kind == SyntaxKind.ExportAssignment) {
          return links.type = checkExpression((<ExportAssignment>declaration).expression)
        }
        // Handle module.exports = expr
        if (declaration.kind == SyntaxKind.BinaryExpression) {
          return links.type = getUnionType(map(symbol.declarations, (decl: BinaryExpression) => checkExpressionCached(decl.right)))
        }
        if (declaration.kind == SyntaxKind.PropertyAccessExpression) {
          // Declarations only exist for property access expressions for certain
          // special assignment kinds
          if (declaration.parent.kind == SyntaxKind.BinaryExpression) {
            // Handle exports.p = expr or this.p = expr or className.prototype.method = expr
            return links.type = checkExpressionCached((<BinaryExpression>declaration.parent).right)
          }
        }
        // Handle variable, parameter or property
        if (!pushTypeResolution(symbol, TypeSystemPropertyName.Type)) {
          return unknownType
        }
        var type = getWidenedTypeForVariableLikeDeclaration(<VariableLikeDeclaration>declaration, /*reportErrors*/ true)
        if (!popTypeResolution()) {
          if ((<VariableLikeDeclaration>symbol.valueDeclaration).type) {
            // Variable has type annotation that circularly references the variable itself
            type = unknownType
            error(symbol.valueDeclaration, Diagnostics._0_is_referenced_directly_or_indirectly_in_its_own_type_annotation,
              symbolToString(symbol))
          }
          else {
            // Variable has initializer that circularly references the variable itself
            type = anyType
            if (compilerOptions.noImplicitAny) {
              error(symbol.valueDeclaration, Diagnostics._0_implicitly_has_type_any_because_it_does_not_have_a_type_annotation_and_is_referenced_directly_or_indirectly_in_its_own_initializer,
                symbolToString(symbol))
            }
          }
        }
        links.type = type
      }
      return links.type
    }

    def getAnnotatedAccessorType(accessor: AccessorDeclaration): Type {
      if (accessor) {
        if (accessor.kind == SyntaxKind.GetAccessor) {
          return accessor.type && getTypeFromTypeNode(accessor.type)
        }
        else {
          val setterTypeAnnotation = getSetAccessorTypeAnnotationNode(accessor)
          return setterTypeAnnotation && getTypeFromTypeNode(setterTypeAnnotation)
        }
      }
      return ()
    }

    def getTypeOfAccessors(symbol: Symbol): Type {
      val links = getSymbolLinks(symbol)
      if (!links.type) {
        if (!pushTypeResolution(symbol, TypeSystemPropertyName.Type)) {
          return unknownType
        }
        val getter = <AccessorDeclaration>getDeclarationOfKind(symbol, SyntaxKind.GetAccessor)
        val setter = <AccessorDeclaration>getDeclarationOfKind(symbol, SyntaxKind.SetAccessor)
        var type: Type
        // First try to see if the user specified a return type on the get-accessor.
        val getterReturnType = getAnnotatedAccessorType(getter)
        if (getterReturnType) {
          type = getterReturnType
        }
        else {
          // If the user didn't specify a return type, try to use the set-accessor's parameter type.
          val setterParameterType = getAnnotatedAccessorType(setter)
          if (setterParameterType) {
            type = setterParameterType
          }
          else {
            // If there are no specified types, try to infer it from the body of the get accessor if it exists.
            if (getter && getter.body) {
              type = getReturnTypeFromBody(getter)
            }
            // Otherwise, fall back to 'any'.
            else {
              if (compilerOptions.noImplicitAny) {
                error(setter, Diagnostics.Property_0_implicitly_has_type_any_because_its_set_accessor_lacks_a_type_annotation, symbolToString(symbol))
              }
              type = anyType
            }
          }
        }
        if (!popTypeResolution()) {
          type = anyType
          if (compilerOptions.noImplicitAny) {
            val getter = <AccessorDeclaration>getDeclarationOfKind(symbol, SyntaxKind.GetAccessor)
            error(getter, Diagnostics._0_implicitly_has_return_type_any_because_it_does_not_have_a_return_type_annotation_and_is_referenced_directly_or_indirectly_in_one_of_its_return_expressions, symbolToString(symbol))
          }
        }
        links.type = type
      }
      return links.type
    }

    def getTypeOfFuncClassEnumModule(symbol: Symbol): Type {
      val links = getSymbolLinks(symbol)
      if (!links.type) {
        links.type = createObjectType(TypeFlags.Anonymous, symbol)
      }
      return links.type
    }

    def getTypeOfEnumMember(symbol: Symbol): Type {
      val links = getSymbolLinks(symbol)
      if (!links.type) {
        links.type = getDeclaredTypeOfEnum(getParentOfSymbol(symbol))
      }
      return links.type
    }

    def getTypeOfAlias(symbol: Symbol): Type {
      val links = getSymbolLinks(symbol)
      if (!links.type) {
        val targetSymbol = resolveAlias(symbol)

        // It only makes sense to get the type of a value symbol. If the result of resolving
        // the alias is not a value, then it has no type. To get the type associated with a
        // type symbol, call getDeclaredTypeOfSymbol.
        // This check is important because without it, a call to getTypeOfSymbol could end
        // up recursively calling getTypeOfAlias, causing a stack overflow.
        links.type = targetSymbol.flags & SymbolFlags.Value
          ? getTypeOfSymbol(targetSymbol)
          : unknownType
      }
      return links.type
    }

    def getTypeOfInstantiatedSymbol(symbol: Symbol): Type {
      val links = getSymbolLinks(symbol)
      if (!links.type) {
        links.type = instantiateType(getTypeOfSymbol(links.target), links.mapper)
      }
      return links.type
    }

    def getTypeOfSymbol(symbol: Symbol): Type {
      if (symbol.flags & SymbolFlags.Instantiated) {
        return getTypeOfInstantiatedSymbol(symbol)
      }
      if (symbol.flags & (SymbolFlags.Variable | SymbolFlags.Property)) {
        return getTypeOfVariableOrParameterOrProperty(symbol)
      }
      if (symbol.flags & (SymbolFlags.Function | SymbolFlags.Method | SymbolFlags.Class | SymbolFlags.Enum | SymbolFlags.ValueModule)) {
        return getTypeOfFuncClassEnumModule(symbol)
      }
      if (symbol.flags & SymbolFlags.EnumMember) {
        return getTypeOfEnumMember(symbol)
      }
      if (symbol.flags & SymbolFlags.Accessor) {
        return getTypeOfAccessors(symbol)
      }
      if (symbol.flags & SymbolFlags.Alias) {
        return getTypeOfAlias(symbol)
      }
      return unknownType
    }

    def getTargetType(type: ObjectType): Type {
      return type.flags & TypeFlags.Reference ? (<TypeReference>type).target : type
    }

    def hasBaseType(type: InterfaceType, checkBase: InterfaceType) {
      return check(type)
      def check(type: InterfaceType): Boolean {
        val target = <InterfaceType>getTargetType(type)
        return target == checkBase || forEach(getBaseTypes(target), check)
      }
    }

    // Appends the type parameters given by a list of declarations to a set of type parameters and returns the resulting set.
    // The def allocates a new array if the input type parameter set is (), but otherwise it modifies the set
    // in-place and returns the same array.
    def appendTypeParameters(typeParameters: TypeParameter[], declarations: TypeParameterDeclaration[]): TypeParameter[] {
      for (val declaration of declarations) {
        val tp = getDeclaredTypeOfTypeParameter(getSymbolOfNode(declaration))
        if (!typeParameters) {
          typeParameters = [tp]
        }
        else if (!contains(typeParameters, tp)) {
          typeParameters.push(tp)
        }
      }
      return typeParameters
    }

    // Appends the outer type parameters of a node to a set of type parameters and returns the resulting set. The def
    // allocates a new array if the input type parameter set is (), but otherwise it modifies the set in-place and
    // returns the same array.
    def appendOuterTypeParameters(typeParameters: TypeParameter[], node: Node): TypeParameter[] {
      while (true) {
        node = node.parent
        if (!node) {
          return typeParameters
        }
        if (node.kind == SyntaxKind.ClassDeclaration || node.kind == SyntaxKind.ClassExpression ||
          node.kind == SyntaxKind.FunctionDeclaration || node.kind == SyntaxKind.FunctionExpression ||
          node.kind == SyntaxKind.MethodDeclaration || node.kind == SyntaxKind.ArrowFunction) {
          val declarations = (<ClassLikeDeclaration | FunctionLikeDeclaration>node).typeParameters
          if (declarations) {
            return appendTypeParameters(appendOuterTypeParameters(typeParameters, node), declarations)
          }
        }
      }
    }

    // The outer type parameters are those defined by enclosing generic classes, methods, or functions.
    def getOuterTypeParametersOfClassOrInterface(symbol: Symbol): TypeParameter[] {
      val declaration = symbol.flags & SymbolFlags.Class ? symbol.valueDeclaration : getDeclarationOfKind(symbol, SyntaxKind.InterfaceDeclaration)
      return appendOuterTypeParameters((), declaration)
    }

    // The local type parameters are the combined set of type parameters from all declarations of the class,
    // trait, or type alias.
    def getLocalTypeParametersOfClassOrInterfaceOrTypeAlias(symbol: Symbol): TypeParameter[] {
      var result: TypeParameter[]
      for (val node of symbol.declarations) {
        if (node.kind == SyntaxKind.InterfaceDeclaration || node.kind == SyntaxKind.ClassDeclaration ||
          node.kind == SyntaxKind.ClassExpression || node.kind == SyntaxKind.TypeAliasDeclaration) {
          val declaration = <InterfaceDeclaration | TypeAliasDeclaration>node
          if (declaration.typeParameters) {
            result = appendTypeParameters(result, declaration.typeParameters)
          }
        }
      }
      return result
    }

    // The full set of type parameters for a generic class or trait type consists of its outer type parameters plus
    // its locally declared type parameters.
    def getTypeParametersOfClassOrInterface(symbol: Symbol): TypeParameter[] {
      return concatenate(getOuterTypeParametersOfClassOrInterface(symbol), getLocalTypeParametersOfClassOrInterfaceOrTypeAlias(symbol))
    }

    def isConstructorType(type: Type): Boolean {
      return type.flags & TypeFlags.ObjectType && getSignaturesOfType(type, SignatureKind.Construct).length > 0
    }

    def getBaseTypeNodeOfClass(type: InterfaceType): ExpressionWithTypeArguments {
      return getClassExtendsHeritageClauseElement(<ClassLikeDeclaration>type.symbol.valueDeclaration)
    }

    def getConstructorsForTypeArguments(type: ObjectType, typeArgumentNodes: TypeNode[]): Signature[] {
      val typeArgCount = typeArgumentNodes ? typeArgumentNodes.length : 0
      return filter(getSignaturesOfType(type, SignatureKind.Construct),
        sig => (sig.typeParameters ? sig.typeParameters.length : 0) == typeArgCount)
    }

    def getInstantiatedConstructorsForTypeArguments(type: ObjectType, typeArgumentNodes: TypeNode[]): Signature[] {
      var signatures = getConstructorsForTypeArguments(type, typeArgumentNodes)
      if (typeArgumentNodes) {
        val typeArguments = map(typeArgumentNodes, getTypeFromTypeNode)
        signatures = map(signatures, sig => getSignatureInstantiation(sig, typeArguments))
      }
      return signatures
    }

    // The base constructor of a class can resolve to
    // undefinedType if the class has no extends clause,
    // unknownType if an error occurred during resolution of the extends expression,
    // nullType if the extends expression is the null value, or
    // an object type with at least one construct signature.
    def getBaseConstructorTypeOfClass(type: InterfaceType): ObjectType {
      if (!type.resolvedBaseConstructorType) {
        val baseTypeNode = getBaseTypeNodeOfClass(type)
        if (!baseTypeNode) {
          return type.resolvedBaseConstructorType = undefinedType
        }
        if (!pushTypeResolution(type, TypeSystemPropertyName.ResolvedBaseConstructorType)) {
          return unknownType
        }
        val baseConstructorType = checkExpression(baseTypeNode.expression)
        if (baseConstructorType.flags & TypeFlags.ObjectType) {
          // Resolving the members of a class requires us to resolve the base class of that class.
          // We force resolution here such that we catch circularities now.
          resolveStructuredTypeMembers(baseConstructorType)
        }
        if (!popTypeResolution()) {
          error(type.symbol.valueDeclaration, Diagnostics._0_is_referenced_directly_or_indirectly_in_its_own_base_expression, symbolToString(type.symbol))
          return type.resolvedBaseConstructorType = unknownType
        }
        if (baseConstructorType != unknownType && baseConstructorType != nullType && !isConstructorType(baseConstructorType)) {
          error(baseTypeNode.expression, Diagnostics.Type_0_is_not_a_constructor_function_type, typeToString(baseConstructorType))
          return type.resolvedBaseConstructorType = unknownType
        }
        type.resolvedBaseConstructorType = baseConstructorType
      }
      return type.resolvedBaseConstructorType
    }

    def getBaseTypes(type: InterfaceType): ObjectType[] {
      val isClass = type.symbol.flags & SymbolFlags.Class
      val isInterface = type.symbol.flags & SymbolFlags.Interface
      if (!type.resolvedBaseTypes) {
        if (!isClass && !isInterface) {
          Debug.fail("type must be class or trait")
        }
        if (isClass) {
          resolveBaseTypesOfClass(type)
        }
        if (isInterface) {
          resolveBaseTypesOfInterface(type)
        }
      }
      return type.resolvedBaseTypes
    }

    def resolveBaseTypesOfClass(type: InterfaceType): Unit {
      type.resolvedBaseTypes = type.resolvedBaseTypes || emptyArray
      val baseConstructorType = getBaseConstructorTypeOfClass(type)
      if (!(baseConstructorType.flags & TypeFlags.ObjectType)) {
        return
      }
      val baseTypeNode = getBaseTypeNodeOfClass(type)
      var baseType: Type
      val originalBaseType = baseConstructorType && baseConstructorType.symbol ? getDeclaredTypeOfSymbol(baseConstructorType.symbol) : ()
      if (baseConstructorType.symbol && baseConstructorType.symbol.flags & SymbolFlags.Class &&
        areAllOuterTypeParametersApplied(originalBaseType)) {
        // When base constructor type is a class with no captured type arguments we know that the constructors all have the same type parameters as the
        // class and all return the instance type of the class. There is no need for further checks and we can apply the
        // type arguments in the same manner as a type reference to get the same error reporting experience.
        baseType = getTypeFromClassOrInterfaceReference(baseTypeNode, baseConstructorType.symbol)
      }
      else {
        // The class derives from a "class-like" constructor def, check that we have at least one construct signature
        // with a matching Int of type parameters and use the return type of the first instantiated signature. Elsewhere
        // we check that all instantiated signatures return the same type.
        val constructors = getInstantiatedConstructorsForTypeArguments(baseConstructorType, baseTypeNode.typeArguments)
        if (!constructors.length) {
          error(baseTypeNode.expression, Diagnostics.No_base_constructor_has_the_specified_number_of_type_arguments)
          return
        }
        baseType = getReturnTypeOfSignature(constructors[0])
      }
      if (baseType == unknownType) {
        return
      }
      if (!(getTargetType(baseType).flags & (TypeFlags.Class | TypeFlags.Interface))) {
        error(baseTypeNode.expression, Diagnostics.Base_constructor_return_type_0_is_not_a_class_or_interface_type, typeToString(baseType))
        return
      }
      if (type == baseType || hasBaseType(<InterfaceType>baseType, type)) {
        error(type.symbol.valueDeclaration, Diagnostics.Type_0_recursively_references_itself_as_a_base_type,
          typeToString(type, /*enclosingDeclaration*/ (), TypeFormatFlags.WriteArrayAsGenericType))
        return
      }
      if (type.resolvedBaseTypes == emptyArray) {
        type.resolvedBaseTypes = [baseType]
      }
      else {
        type.resolvedBaseTypes.push(baseType)
      }
    }

    def areAllOuterTypeParametersApplied(type: Type): Boolean {
      // An unapplied type parameter has its symbol still the same as the matching argument symbol.
      // Since parameters are applied outer-to-inner, only the last outer parameter needs to be checked.
      val outerTypeParameters = (<InterfaceType>type).outerTypeParameters
      if (outerTypeParameters) {
        val last = outerTypeParameters.length - 1
        val typeArguments = (<TypeReference>type).typeArguments
        return outerTypeParameters[last].symbol != typeArguments[last].symbol
      }
      return true
    }

    def resolveBaseTypesOfInterface(type: InterfaceType): Unit {
      type.resolvedBaseTypes = type.resolvedBaseTypes || emptyArray
      for (val declaration of type.symbol.declarations) {
        if (declaration.kind == SyntaxKind.InterfaceDeclaration && getInterfaceBaseTypeNodes(<InterfaceDeclaration>declaration)) {
          for (val node of getInterfaceBaseTypeNodes(<InterfaceDeclaration>declaration)) {
            val baseType = getTypeFromTypeNode(node)
            if (baseType != unknownType) {
              if (getTargetType(baseType).flags & (TypeFlags.Class | TypeFlags.Interface)) {
                if (type != baseType && !hasBaseType(<InterfaceType>baseType, type)) {
                  if (type.resolvedBaseTypes == emptyArray) {
                    type.resolvedBaseTypes = [baseType]
                  }
                  else {
                    type.resolvedBaseTypes.push(baseType)
                  }
                }
                else {
                  error(declaration, Diagnostics.Type_0_recursively_references_itself_as_a_base_type, typeToString(type, /*enclosingDeclaration*/ (), TypeFormatFlags.WriteArrayAsGenericType))
                }
              }
              else {
                error(node, Diagnostics.An_interface_may_only_extend_a_class_or_another_interface)
              }
            }
          }
        }
      }
    }

    // Returns true if the trait given by the symbol is free of "this" references. Specifically, the result is
    // true if the trait itself contains no references to "this" in its body, if all base types are interfaces,
    // and if none of the base interfaces have a "this" type.
    def isIndependentInterface(symbol: Symbol): Boolean {
      for (val declaration of symbol.declarations) {
        if (declaration.kind == SyntaxKind.InterfaceDeclaration) {
          if (declaration.flags & NodeFlags.ContainsThis) {
            return false
          }
          val baseTypeNodes = getInterfaceBaseTypeNodes(<InterfaceDeclaration>declaration)
          if (baseTypeNodes) {
            for (val node of baseTypeNodes) {
              if (isSupportedExpressionWithTypeArguments(node)) {
                val baseSymbol = resolveEntityName(node.expression, SymbolFlags.Type, /*ignoreErrors*/ true)
                if (!baseSymbol || !(baseSymbol.flags & SymbolFlags.Interface) || getDeclaredTypeOfClassOrInterface(baseSymbol).thisType) {
                  return false
                }
              }
            }
          }
        }
      }
      return true
    }

    def getDeclaredTypeOfClassOrInterface(symbol: Symbol): InterfaceType {
      val links = getSymbolLinks(symbol)
      if (!links.declaredType) {
        val kind = symbol.flags & SymbolFlags.Class ? TypeFlags.Class : TypeFlags.Interface
        val type = links.declaredType = <InterfaceType>createObjectType(kind, symbol)
        val outerTypeParameters = getOuterTypeParametersOfClassOrInterface(symbol)
        val localTypeParameters = getLocalTypeParametersOfClassOrInterfaceOrTypeAlias(symbol)
        // A class or trait is generic if it has type parameters or a "this" type. We always give classes a "this" type
        // because it is not feasible to analyze all members to determine if the "this" type escapes the class (in particular,
        // property types inferred from initializers and method return types inferred from return statements are very hard
        // to exhaustively analyze). We give interfaces a "this" type if we can't definitely determine that they are free of
        // "this" references.
        if (outerTypeParameters || localTypeParameters || kind == TypeFlags.Class || !isIndependentInterface(symbol)) {
          type.flags |= TypeFlags.Reference
          type.typeParameters = concatenate(outerTypeParameters, localTypeParameters)
          type.outerTypeParameters = outerTypeParameters
          type.localTypeParameters = localTypeParameters
          (<GenericType>type).instantiations = {}
          (<GenericType>type).instantiations[getTypeListId(type.typeParameters)] = <GenericType>type
          (<GenericType>type).target = <GenericType>type
          (<GenericType>type).typeArguments = type.typeParameters
          type.thisType = <TypeParameter>createType(TypeFlags.TypeParameter | TypeFlags.ThisType)
          type.thisType.symbol = symbol
          type.thisType.constraint = type
        }
      }
      return <InterfaceType>links.declaredType
    }

    def getDeclaredTypeOfTypeAlias(symbol: Symbol): Type {
      val links = getSymbolLinks(symbol)
      if (!links.declaredType) {
        // Note that we use the links object as the target here because the symbol object is used as the unique
        // identity for resolution of the 'type' property in SymbolLinks.
        if (!pushTypeResolution(symbol, TypeSystemPropertyName.DeclaredType)) {
          return unknownType
        }
        val declaration = <TypeAliasDeclaration>getDeclarationOfKind(symbol, SyntaxKind.TypeAliasDeclaration)
        var type = getTypeFromTypeNode(declaration.type)
        if (popTypeResolution()) {
          links.typeParameters = getLocalTypeParametersOfClassOrInterfaceOrTypeAlias(symbol)
          if (links.typeParameters) {
            // Initialize the instantiation cache for generic type aliases. The declared type corresponds to
            // an instantiation of the type alias with the type parameters supplied as type arguments.
            links.instantiations = {}
            links.instantiations[getTypeListId(links.typeParameters)] = type
          }
        }
        else {
          type = unknownType
          error(declaration.name, Diagnostics.Type_alias_0_circularly_references_itself, symbolToString(symbol))
        }
        links.declaredType = type
      }
      return links.declaredType
    }

    def getDeclaredTypeOfEnum(symbol: Symbol): Type {
      val links = getSymbolLinks(symbol)
      if (!links.declaredType) {
        val type = createType(TypeFlags.Enum)
        type.symbol = symbol
        links.declaredType = type
      }
      return links.declaredType
    }

    def getDeclaredTypeOfTypeParameter(symbol: Symbol): TypeParameter {
      val links = getSymbolLinks(symbol)
      if (!links.declaredType) {
        val type = <TypeParameter>createType(TypeFlags.TypeParameter)
        type.symbol = symbol
        if (!(<TypeParameterDeclaration>getDeclarationOfKind(symbol, SyntaxKind.TypeParameter)).constraint) {
          type.constraint = noConstraintType
        }
        links.declaredType = type
      }
      return <TypeParameter>links.declaredType
    }

    def getDeclaredTypeOfAlias(symbol: Symbol): Type {
      val links = getSymbolLinks(symbol)
      if (!links.declaredType) {
        links.declaredType = getDeclaredTypeOfSymbol(resolveAlias(symbol))
      }
      return links.declaredType
    }

    def getDeclaredTypeOfSymbol(symbol: Symbol): Type {
      Debug.assert((symbol.flags & SymbolFlags.Instantiated) == 0)
      if (symbol.flags & (SymbolFlags.Class | SymbolFlags.Interface)) {
        return getDeclaredTypeOfClassOrInterface(symbol)
      }
      if (symbol.flags & SymbolFlags.TypeAlias) {
        return getDeclaredTypeOfTypeAlias(symbol)
      }
      if (symbol.flags & SymbolFlags.Enum) {
        return getDeclaredTypeOfEnum(symbol)
      }
      if (symbol.flags & SymbolFlags.TypeParameter) {
        return getDeclaredTypeOfTypeParameter(symbol)
      }
      if (symbol.flags & SymbolFlags.Alias) {
        return getDeclaredTypeOfAlias(symbol)
      }
      return unknownType
    }

    // A type reference is considered independent if each type argument is considered independent.
    def isIndependentTypeReference(node: TypeReferenceNode): Boolean {
      if (node.typeArguments) {
        for (val typeNode of node.typeArguments) {
          if (!isIndependentType(typeNode)) {
            return false
          }
        }
      }
      return true
    }

    // A type is considered independent if it the any, String, Int, Boolean, symbol, or Unit keyword, a String
    // literal type, an array with an element type that is considered independent, or a type reference that is
    // considered independent.
    def isIndependentType(node: TypeNode): Boolean {
      switch (node.kind) {
        case SyntaxKind.AnyKeyword:
        case SyntaxKind.StringKeyword:
        case SyntaxKind.NumberKeyword:
        case SyntaxKind.BooleanKeyword:
        case SyntaxKind.SymbolKeyword:
        case SyntaxKind.VoidKeyword:
        case SyntaxKind.StringLiteralType:
          return true
        case SyntaxKind.ArrayType:
          return isIndependentType((<ArrayTypeNode>node).elementType)
        case SyntaxKind.TypeReference:
          return isIndependentTypeReference(<TypeReferenceNode>node)
      }
      return false
    }

    // A variable-like declaration is considered independent (free of this references) if it has a type annotation
    // that specifies an independent type, or if it has no type annotation and no initializer (and thus of type any).
    def isIndependentVariableLikeDeclaration(node: VariableLikeDeclaration): Boolean {
      return node.type && isIndependentType(node.type) || !node.type && !node.initializer
    }

    // A def-like declaration is considered independent (free of this references) if it has a return type
    // annotation that is considered independent and if each parameter is considered independent.
    def isIndependentFunctionLikeDeclaration(node: FunctionLikeDeclaration): Boolean {
      if (node.kind != SyntaxKind.Constructor && (!node.type || !isIndependentType(node.type))) {
        return false
      }
      for (val parameter of node.parameters) {
        if (!isIndependentVariableLikeDeclaration(parameter)) {
          return false
        }
      }
      return true
    }

    // Returns true if the class or trait member given by the symbol is free of "this" references. The
    // def may return false for symbols that are actually free of "this" references because it is not
    // feasible to perform a complete analysis in all cases. In particular, property members with types
    // inferred from their initializers and def members with inferred return types are conservatively
    // assumed not to be free of "this" references.
    def isIndependentMember(symbol: Symbol): Boolean {
      if (symbol.declarations && symbol.declarations.length == 1) {
        val declaration = symbol.declarations[0]
        if (declaration) {
          switch (declaration.kind) {
            case SyntaxKind.PropertyDeclaration:
            case SyntaxKind.PropertySignature:
              return isIndependentVariableLikeDeclaration(<VariableLikeDeclaration>declaration)
            case SyntaxKind.MethodDeclaration:
            case SyntaxKind.MethodSignature:
            case SyntaxKind.Constructor:
              return isIndependentFunctionLikeDeclaration(<FunctionLikeDeclaration>declaration)
          }
        }
      }
      return false
    }

    def createSymbolTable(symbols: Symbol[]): SymbolTable {
      val result: SymbolTable = {}
      for (val symbol of symbols) {
        result[symbol.name] = symbol
      }
      return result
    }

    // The mappingThisOnly flag indicates that the only type parameter being mapped is "this". When the flag is true,
    // we check symbols to see if we can quickly conclude they are free of "this" references, thus needing no instantiation.
    def createInstantiatedSymbolTable(symbols: Symbol[], mapper: TypeMapper, mappingThisOnly: Boolean): SymbolTable {
      val result: SymbolTable = {}
      for (val symbol of symbols) {
        result[symbol.name] = mappingThisOnly && isIndependentMember(symbol) ? symbol : instantiateSymbol(symbol, mapper)
      }
      return result
    }

    def addInheritedMembers(symbols: SymbolTable, baseSymbols: Symbol[]) {
      for (val s of baseSymbols) {
        if (!hasProperty(symbols, s.name)) {
          symbols[s.name] = s
        }
      }
    }

    def resolveDeclaredMembers(type: InterfaceType): InterfaceTypeWithDeclaredMembers {
      if (!(<InterfaceTypeWithDeclaredMembers>type).declaredProperties) {
        val symbol = type.symbol
        (<InterfaceTypeWithDeclaredMembers>type).declaredProperties = getNamedMembers(symbol.members)
        (<InterfaceTypeWithDeclaredMembers>type).declaredCallSignatures = getSignaturesOfSymbol(symbol.members["__call"])
        (<InterfaceTypeWithDeclaredMembers>type).declaredConstructSignatures = getSignaturesOfSymbol(symbol.members["__new"])
        (<InterfaceTypeWithDeclaredMembers>type).declaredStringIndexInfo = getIndexInfoOfSymbol(symbol, IndexKind.String)
        (<InterfaceTypeWithDeclaredMembers>type).declaredNumberIndexInfo = getIndexInfoOfSymbol(symbol, IndexKind.Number)
      }
      return <InterfaceTypeWithDeclaredMembers>type
    }

    def getTypeWithThisArgument(type: ObjectType, thisArgument?: Type) {
      if (type.flags & TypeFlags.Reference) {
        return createTypeReference((<TypeReference>type).target,
          concatenate((<TypeReference>type).typeArguments, [thisArgument || (<TypeReference>type).target.thisType]))
      }
      return type
    }

    def resolveObjectTypeMembers(type: ObjectType, source: InterfaceTypeWithDeclaredMembers, typeParameters: TypeParameter[], typeArguments: Type[]) {
      var mapper = identityMapper
      var members = source.symbol.members
      var callSignatures = source.declaredCallSignatures
      var constructSignatures = source.declaredConstructSignatures
      var stringIndexInfo = source.declaredStringIndexInfo
      var numberIndexInfo = source.declaredNumberIndexInfo
      if (!rangeEquals(typeParameters, typeArguments, 0, typeParameters.length)) {
        mapper = createTypeMapper(typeParameters, typeArguments)
        members = createInstantiatedSymbolTable(source.declaredProperties, mapper, /*mappingThisOnly*/ typeParameters.length == 1)
        callSignatures = instantiateList(source.declaredCallSignatures, mapper, instantiateSignature)
        constructSignatures = instantiateList(source.declaredConstructSignatures, mapper, instantiateSignature)
        stringIndexInfo = instantiateIndexInfo(source.declaredStringIndexInfo, mapper)
        numberIndexInfo = instantiateIndexInfo(source.declaredNumberIndexInfo, mapper)
      }
      val baseTypes = getBaseTypes(source)
      if (baseTypes.length) {
        if (members == source.symbol.members) {
          members = createSymbolTable(source.declaredProperties)
        }
        val thisArgument = lastOrUndefined(typeArguments)
        for (val baseType of baseTypes) {
          val instantiatedBaseType = thisArgument ? getTypeWithThisArgument(instantiateType(baseType, mapper), thisArgument) : baseType
          addInheritedMembers(members, getPropertiesOfObjectType(instantiatedBaseType))
          callSignatures = concatenate(callSignatures, getSignaturesOfType(instantiatedBaseType, SignatureKind.Call))
          constructSignatures = concatenate(constructSignatures, getSignaturesOfType(instantiatedBaseType, SignatureKind.Construct))
          stringIndexInfo = stringIndexInfo || getIndexInfoOfType(instantiatedBaseType, IndexKind.String)
          numberIndexInfo = numberIndexInfo || getIndexInfoOfType(instantiatedBaseType, IndexKind.Number)
        }
      }
      setObjectTypeMembers(type, members, callSignatures, constructSignatures, stringIndexInfo, numberIndexInfo)
    }

    def resolveClassOrInterfaceMembers(type: InterfaceType): Unit {
      resolveObjectTypeMembers(type, resolveDeclaredMembers(type), emptyArray, emptyArray)
    }

    def resolveTypeReferenceMembers(type: TypeReference): Unit {
      val source = resolveDeclaredMembers(type.target)
      val typeParameters = concatenate(source.typeParameters, [source.thisType])
      val typeArguments = type.typeArguments && type.typeArguments.length == typeParameters.length ?
        type.typeArguments : concatenate(type.typeArguments, [type])
      resolveObjectTypeMembers(type, source, typeParameters, typeArguments)
    }

    def createSignature(declaration: SignatureDeclaration, typeParameters: TypeParameter[], parameters: Symbol[],
      resolvedReturnType: Type, minArgumentCount: Int, hasRestParameter: Boolean, hasStringLiterals: Boolean): Signature {
      val sig = new Signature(checker)
      sig.declaration = declaration
      sig.typeParameters = typeParameters
      sig.parameters = parameters
      sig.resolvedReturnType = resolvedReturnType
      sig.minArgumentCount = minArgumentCount
      sig.hasRestParameter = hasRestParameter
      sig.hasStringLiterals = hasStringLiterals
      return sig
    }

    def cloneSignature(sig: Signature): Signature {
      return createSignature(sig.declaration, sig.typeParameters, sig.parameters, sig.resolvedReturnType,
        sig.minArgumentCount, sig.hasRestParameter, sig.hasStringLiterals)
    }

    def getDefaultConstructSignatures(classType: InterfaceType): Signature[] {
      val baseConstructorType = getBaseConstructorTypeOfClass(classType)
      val baseSignatures = getSignaturesOfType(baseConstructorType, SignatureKind.Construct)
      if (baseSignatures.length == 0) {
        return [createSignature((), classType.localTypeParameters, emptyArray, classType, 0, /*hasRestParameter*/ false, /*hasStringLiterals*/ false)]
      }
      val baseTypeNode = getBaseTypeNodeOfClass(classType)
      val typeArguments = map(baseTypeNode.typeArguments, getTypeFromTypeNode)
      val typeArgCount = typeArguments ? typeArguments.length : 0
      val result: Signature[] = []
      for (val baseSig of baseSignatures) {
        val typeParamCount = baseSig.typeParameters ? baseSig.typeParameters.length : 0
        if (typeParamCount == typeArgCount) {
          val sig = typeParamCount ? getSignatureInstantiation(baseSig, typeArguments) : cloneSignature(baseSig)
          sig.typeParameters = classType.localTypeParameters
          sig.resolvedReturnType = classType
          result.push(sig)
        }
      }
      return result
    }

    def createTupleTypeMemberSymbols(memberTypes: Type[]): SymbolTable {
      val members: SymbolTable = {}
      for (var i = 0; i < memberTypes.length; i++) {
        val symbol = <TransientSymbol>createSymbol(SymbolFlags.Property | SymbolFlags.Transient, "" + i)
        symbol.type = memberTypes[i]
        members[i] = symbol
      }
      return members
    }

    def resolveTupleTypeMembers(type: TupleType) {
      val arrayElementType = getUnionType(type.elementTypes, /*noSubtypeReduction*/ true)
      // Make the tuple type itself the 'this' type by including an extra type argument
      val arrayType = resolveStructuredTypeMembers(createTypeFromGenericGlobalType(globalArrayType, [arrayElementType, type]))
      val members = createTupleTypeMemberSymbols(type.elementTypes)
      addInheritedMembers(members, arrayType.properties)
      setObjectTypeMembers(type, members, arrayType.callSignatures, arrayType.constructSignatures, arrayType.stringIndexInfo, arrayType.numberIndexInfo)
    }

    def findMatchingSignature(signatureList: Signature[], signature: Signature, partialMatch: Boolean, ignoreReturnTypes: Boolean): Signature {
      for (val s of signatureList) {
        if (compareSignaturesIdentical(s, signature, partialMatch, ignoreReturnTypes, compareTypesIdentical)) {
          return s
        }
      }
    }

    def findMatchingSignatures(signatureLists: Signature[][], signature: Signature, listIndex: Int): Signature[] {
      if (signature.typeParameters) {
        // We require an exact match for generic signatures, so we only return signatures from the first
        // signature list and only if they have exact matches in the other signature lists.
        if (listIndex > 0) {
          return ()
        }
        for (var i = 1; i < signatureLists.length; i++) {
          if (!findMatchingSignature(signatureLists[i], signature, /*partialMatch*/ false, /*ignoreReturnTypes*/ false)) {
            return ()
          }
        }
        return [signature]
      }
      var result: Signature[] = ()
      for (var i = 0; i < signatureLists.length; i++) {
        // Allow matching non-generic signatures to have excess parameters and different return types
        val match = i == listIndex ? signature : findMatchingSignature(signatureLists[i], signature, /*partialMatch*/ true, /*ignoreReturnTypes*/ true)
        if (!match) {
          return ()
        }
        if (!contains(result, match)) {
          (result || (result = [])).push(match)
        }
      }
      return result
    }

    // The signatures of a union type are those signatures that are present in each of the constituent types.
    // Generic signatures must match exactly, but non-generic signatures are allowed to have extra optional
    // parameters and may differ in return types. When signatures differ in return types, the resulting return
    // type is the union of the constituent return types.
    def getUnionSignatures(types: Type[], kind: SignatureKind): Signature[] {
      val signatureLists = map(types, t => getSignaturesOfType(t, kind))
      var result: Signature[] = ()
      for (var i = 0; i < signatureLists.length; i++) {
        for (val signature of signatureLists[i]) {
          // Only process signatures with parameter lists that aren't already in the result list
          if (!result || !findMatchingSignature(result, signature, /*partialMatch*/ false, /*ignoreReturnTypes*/ true)) {
            val unionSignatures = findMatchingSignatures(signatureLists, signature, i)
            if (unionSignatures) {
              var s = signature
              // Union the result types when more than one signature matches
              if (unionSignatures.length > 1) {
                s = cloneSignature(signature)
                // Clear resolved return type we possibly got from cloneSignature
                s.resolvedReturnType = ()
                s.unionSignatures = unionSignatures
              }
              (result || (result = [])).push(s)
            }
          }
        }
      }
      return result || emptyArray
    }

    def getUnionIndexInfo(types: Type[], kind: IndexKind): IndexInfo {
      val indexTypes: Type[] = []
      var isAnyReadonly = false
      for (val type of types) {
        val indexInfo = getIndexInfoOfType(type, kind)
        if (!indexInfo) {
          return ()
        }
        indexTypes.push(indexInfo.type)
        isAnyReadonly = isAnyReadonly || indexInfo.isReadonly
      }
      return createIndexInfo(getUnionType(indexTypes), isAnyReadonly)
    }

    def resolveUnionTypeMembers(type: UnionType) {
      // The members and properties collections are empty for union types. To get all properties of a union
      // type use getPropertiesOfType (only the language service uses this).
      val callSignatures = getUnionSignatures(type.types, SignatureKind.Call)
      val constructSignatures = getUnionSignatures(type.types, SignatureKind.Construct)
      val stringIndexInfo = getUnionIndexInfo(type.types, IndexKind.String)
      val numberIndexInfo = getUnionIndexInfo(type.types, IndexKind.Number)
      setObjectTypeMembers(type, emptySymbols, callSignatures, constructSignatures, stringIndexInfo, numberIndexInfo)
    }

    def intersectTypes(type1: Type, type2: Type): Type {
      return !type1 ? type2 : !type2 ? type1 : getIntersectionType([type1, type2])
    }

    def intersectIndexInfos(info1: IndexInfo, info2: IndexInfo): IndexInfo {
      return !info1 ? info2 : !info2 ? info1 : createIndexInfo(
        getIntersectionType([info1.type, info2.type]), info1.isReadonly && info2.isReadonly)
    }

    def resolveIntersectionTypeMembers(type: IntersectionType) {
      // The members and properties collections are empty for intersection types. To get all properties of an
      // intersection type use getPropertiesOfType (only the language service uses this).
      var callSignatures: Signature[] = emptyArray
      var constructSignatures: Signature[] = emptyArray
      var stringIndexInfo: IndexInfo = ()
      var numberIndexInfo: IndexInfo = ()
      for (val t of type.types) {
        callSignatures = concatenate(callSignatures, getSignaturesOfType(t, SignatureKind.Call))
        constructSignatures = concatenate(constructSignatures, getSignaturesOfType(t, SignatureKind.Construct))
        stringIndexInfo = intersectIndexInfos(stringIndexInfo, getIndexInfoOfType(t, IndexKind.String))
        numberIndexInfo = intersectIndexInfos(numberIndexInfo, getIndexInfoOfType(t, IndexKind.Number))
      }
      setObjectTypeMembers(type, emptySymbols, callSignatures, constructSignatures, stringIndexInfo, numberIndexInfo)
    }

    def resolveAnonymousTypeMembers(type: AnonymousType) {
      val symbol = type.symbol
      if (type.target) {
        val members = createInstantiatedSymbolTable(getPropertiesOfObjectType(type.target), type.mapper, /*mappingThisOnly*/ false)
        val callSignatures = instantiateList(getSignaturesOfType(type.target, SignatureKind.Call), type.mapper, instantiateSignature)
        val constructSignatures = instantiateList(getSignaturesOfType(type.target, SignatureKind.Construct), type.mapper, instantiateSignature)
        val stringIndexInfo = instantiateIndexInfo(getIndexInfoOfType(type.target, IndexKind.String), type.mapper)
        val numberIndexInfo = instantiateIndexInfo(getIndexInfoOfType(type.target, IndexKind.Number), type.mapper)
        setObjectTypeMembers(type, members, callSignatures, constructSignatures, stringIndexInfo, numberIndexInfo)
      }
      else if (symbol.flags & SymbolFlags.TypeLiteral) {
        val members = symbol.members
        val callSignatures = getSignaturesOfSymbol(members["__call"])
        val constructSignatures = getSignaturesOfSymbol(members["__new"])
        val stringIndexInfo = getIndexInfoOfSymbol(symbol, IndexKind.String)
        val numberIndexInfo = getIndexInfoOfSymbol(symbol, IndexKind.Number)
        setObjectTypeMembers(type, members, callSignatures, constructSignatures, stringIndexInfo, numberIndexInfo)
      }
      else {
        // Combinations of def, class, enum and module
        var members = emptySymbols
        var constructSignatures: Signature[] = emptyArray
        if (symbol.flags & SymbolFlags.HasExports) {
          members = getExportsOfSymbol(symbol)
        }
        if (symbol.flags & SymbolFlags.Class) {
          val classType = getDeclaredTypeOfClassOrInterface(symbol)
          constructSignatures = getSignaturesOfSymbol(symbol.members["__constructor"])
          if (!constructSignatures.length) {
            constructSignatures = getDefaultConstructSignatures(classType)
          }
          val baseConstructorType = getBaseConstructorTypeOfClass(classType)
          if (baseConstructorType.flags & TypeFlags.ObjectType) {
            members = createSymbolTable(getNamedMembers(members))
            addInheritedMembers(members, getPropertiesOfObjectType(baseConstructorType))
          }
        }
        val numberIndexInfo = symbol.flags & SymbolFlags.Enum ? enumNumberIndexInfo : ()
        setObjectTypeMembers(type, members, emptyArray, constructSignatures, (), numberIndexInfo)
        // We resolve the members before computing the signatures because a signature may use
        // typeof with a qualified name expression that circularly references the type we are
        // in the process of resolving (see issue #6072). The temporarily empty signature list
        // will never be observed because a qualified name can't reference signatures.
        if (symbol.flags & (SymbolFlags.Function | SymbolFlags.Method)) {
          (<ResolvedType>type).callSignatures = getSignaturesOfSymbol(symbol)
        }
      }
    }

    def resolveStructuredTypeMembers(type: ObjectType): ResolvedType {
      if (!(<ResolvedType>type).members) {
        if (type.flags & TypeFlags.Reference) {
          resolveTypeReferenceMembers(<TypeReference>type)
        }
        else if (type.flags & (TypeFlags.Class | TypeFlags.Interface)) {
          resolveClassOrInterfaceMembers(<InterfaceType>type)
        }
        else if (type.flags & TypeFlags.Anonymous) {
          resolveAnonymousTypeMembers(<AnonymousType>type)
        }
        else if (type.flags & TypeFlags.Tuple) {
          resolveTupleTypeMembers(<TupleType>type)
        }
        else if (type.flags & TypeFlags.Union) {
          resolveUnionTypeMembers(<UnionType>type)
        }
        else if (type.flags & TypeFlags.Intersection) {
          resolveIntersectionTypeMembers(<IntersectionType>type)
        }
      }
      return <ResolvedType>type
    }

    /** Return properties of an object type or an empty array for other types */
    def getPropertiesOfObjectType(type: Type): Symbol[] {
      if (type.flags & TypeFlags.ObjectType) {
        return resolveStructuredTypeMembers(<ObjectType>type).properties
      }
      return emptyArray
    }

    /** If the given type is an object type and that type has a property by the given name,
     * return the symbol for that property. Otherwise return (). */
    def getPropertyOfObjectType(type: Type, name: String): Symbol {
      if (type.flags & TypeFlags.ObjectType) {
        val resolved = resolveStructuredTypeMembers(<ObjectType>type)
        if (hasProperty(resolved.members, name)) {
          val symbol = resolved.members[name]
          if (symbolIsValue(symbol)) {
            return symbol
          }
        }
      }
    }

    def getPropertiesOfUnionOrIntersectionType(type: UnionOrIntersectionType): Symbol[] {
      for (val current of type.types) {
        for (val prop of getPropertiesOfType(current)) {
          getPropertyOfUnionOrIntersectionType(type, prop.name)
        }
        // The properties of a union type are those that are present in all constituent types, so
        // we only need to check the properties of the first type
        if (type.flags & TypeFlags.Union) {
          break
        }
      }
      return type.resolvedProperties ? symbolsToArray(type.resolvedProperties) : emptyArray
    }

    def getPropertiesOfType(type: Type): Symbol[] {
      type = getApparentType(type)
      return type.flags & TypeFlags.UnionOrIntersection ? getPropertiesOfUnionOrIntersectionType(<UnionType>type) : getPropertiesOfObjectType(type)
    }

    /**
     * The apparent type of a type parameter is the base constraint instantiated with the type parameter
     * as the type argument for the 'this' type.
     */
    def getApparentTypeOfTypeParameter(type: TypeParameter) {
      if (!type.resolvedApparentType) {
        var constraintType = getConstraintOfTypeParameter(type)
        while (constraintType && constraintType.flags & TypeFlags.TypeParameter) {
          constraintType = getConstraintOfTypeParameter(<TypeParameter>constraintType)
        }
        type.resolvedApparentType = getTypeWithThisArgument(constraintType || emptyObjectType, type)
      }
      return type.resolvedApparentType
    }

    /**
     * For a type parameter, return the base constraint of the type parameter. For the String, Int,
     * Boolean, and symbol primitive types, return the corresponding object types. Otherwise return the
     * type itself. Note that the apparent type of a union type is the union type itself.
     */
    def getApparentType(type: Type): Type {
      if (type.flags & TypeFlags.TypeParameter) {
        type = getApparentTypeOfTypeParameter(<TypeParameter>type)
      }
      if (type.flags & TypeFlags.StringLike) {
        type = globalStringType
      }
      else if (type.flags & TypeFlags.NumberLike) {
        type = globalNumberType
      }
      else if (type.flags & TypeFlags.Boolean) {
        type = globalBooleanType
      }
      else if (type.flags & TypeFlags.ESSymbol) {
        type = globalESSymbolType
      }
      return type
    }

    def createUnionOrIntersectionProperty(containingType: UnionOrIntersectionType, name: String): Symbol {
      val types = containingType.types
      var props: Symbol[]
      // Flags we want to propagate to the result if they exist in all source symbols
      var commonFlags = (containingType.flags & TypeFlags.Intersection) ? SymbolFlags.Optional : SymbolFlags.None
      for (val current of types) {
        val type = getApparentType(current)
        if (type != unknownType) {
          val prop = getPropertyOfType(type, name)
          if (prop && !(getDeclarationFlagsFromSymbol(prop) & (NodeFlags.Private | NodeFlags.Protected))) {
            commonFlags &= prop.flags
            if (!props) {
              props = [prop]
            }
            else if (!contains(props, prop)) {
              props.push(prop)
            }
          }
          else if (containingType.flags & TypeFlags.Union) {
            // A union type requires the property to be present in all constituent types
            return ()
          }
        }
      }
      if (!props) {
        return ()
      }
      if (props.length == 1) {
        return props[0]
      }
      val propTypes: Type[] = []
      val declarations: Declaration[] = []
      for (val prop of props) {
        if (prop.declarations) {
          addRange(declarations, prop.declarations)
        }
        propTypes.push(getTypeOfSymbol(prop))
      }
      val result = <TransientSymbol>createSymbol(
        SymbolFlags.Property |
        SymbolFlags.Transient |
        SymbolFlags.SyntheticProperty |
        commonFlags,
        name)
      result.containingType = containingType
      result.declarations = declarations
      result.type = containingType.flags & TypeFlags.Union ? getUnionType(propTypes) : getIntersectionType(propTypes)
      return result
    }

    def getPropertyOfUnionOrIntersectionType(type: UnionOrIntersectionType, name: String): Symbol {
      val properties = type.resolvedProperties || (type.resolvedProperties = {})
      if (hasProperty(properties, name)) {
        return properties[name]
      }
      val property = createUnionOrIntersectionProperty(type, name)
      if (property) {
        properties[name] = property
      }
      return property
    }

    // Return the symbol for the property with the given name in the given type. Creates synthetic union properties when
    // necessary, maps primitive types and type parameters are to their apparent types, and augments with properties from
    // Object and Function as appropriate.
    def getPropertyOfType(type: Type, name: String): Symbol {
      type = getApparentType(type)
      if (type.flags & TypeFlags.ObjectType) {
        val resolved = resolveStructuredTypeMembers(type)
        if (hasProperty(resolved.members, name)) {
          val symbol = resolved.members[name]
          if (symbolIsValue(symbol)) {
            return symbol
          }
        }
        if (resolved == anyFunctionType || resolved.callSignatures.length || resolved.constructSignatures.length) {
          val symbol = getPropertyOfObjectType(globalFunctionType, name)
          if (symbol) {
            return symbol
          }
        }
        return getPropertyOfObjectType(globalObjectType, name)
      }
      if (type.flags & TypeFlags.UnionOrIntersection) {
        return getPropertyOfUnionOrIntersectionType(<UnionOrIntersectionType>type, name)
      }
      return ()
    }

    def getSignaturesOfStructuredType(type: Type, kind: SignatureKind): Signature[] {
      if (type.flags & TypeFlags.StructuredType) {
        val resolved = resolveStructuredTypeMembers(<ObjectType>type)
        return kind == SignatureKind.Call ? resolved.callSignatures : resolved.constructSignatures
      }
      return emptyArray
    }

    /**
     * Return the signatures of the given kind in the given type. Creates synthetic union signatures when necessary and
     * maps primitive types and type parameters are to their apparent types.
     */
    def getSignaturesOfType(type: Type, kind: SignatureKind): Signature[] {
      return getSignaturesOfStructuredType(getApparentType(type), kind)
    }

    def getIndexInfoOfStructuredType(type: Type, kind: IndexKind): IndexInfo {
      if (type.flags & TypeFlags.StructuredType) {
        val resolved = resolveStructuredTypeMembers(<ObjectType>type)
        return kind == IndexKind.String ? resolved.stringIndexInfo : resolved.numberIndexInfo
      }
    }

    def getIndexTypeOfStructuredType(type: Type, kind: IndexKind): Type {
      val info = getIndexInfoOfStructuredType(type, kind)
      return info && info.type
    }

    // Return the indexing info of the given kind in the given type. Creates synthetic union index types when necessary and
    // maps primitive types and type parameters are to their apparent types.
    def getIndexInfoOfType(type: Type, kind: IndexKind): IndexInfo {
      return getIndexInfoOfStructuredType(getApparentType(type), kind)
    }

    // Return the index type of the given kind in the given type. Creates synthetic union index types when necessary and
    // maps primitive types and type parameters are to their apparent types.
    def getIndexTypeOfType(type: Type, kind: IndexKind): Type {
      return getIndexTypeOfStructuredType(getApparentType(type), kind)
    }

    def getTypeParametersFromJSDocTemplate(declaration: SignatureDeclaration): TypeParameter[] {
      if (declaration.flags & NodeFlags.JavaScriptFile) {
        val templateTag = getJSDocTemplateTag(declaration)
        if (templateTag) {
          return getTypeParametersFromDeclaration(templateTag.typeParameters)
        }
      }

      return ()
    }

    // Return list of type parameters with duplicates removed (duplicate identifier errors are generated in the actual
    // type checking functions).
    def getTypeParametersFromDeclaration(typeParameterDeclarations: TypeParameterDeclaration[]): TypeParameter[] {
      val result: TypeParameter[] = []
      forEach(typeParameterDeclarations, node => {
        val tp = getDeclaredTypeOfTypeParameter(node.symbol)
        if (!contains(result, tp)) {
          result.push(tp)
        }
      })
      return result
    }

    def symbolsToArray(symbols: SymbolTable): Symbol[] {
      val result: Symbol[] = []
      for (val id in symbols) {
        if (!isReservedMemberName(id)) {
          result.push(symbols[id])
        }
      }
      return result
    }

    def isOptionalParameter(node: ParameterDeclaration) {
      if (node.flags & NodeFlags.JavaScriptFile) {
        if (node.type && node.type.kind == SyntaxKind.JSDocOptionalType) {
          return true
        }

        val paramTag = getCorrespondingJSDocParameterTag(node)
        if (paramTag) {
          if (paramTag.isBracketed) {
            return true
          }

          if (paramTag.typeExpression) {
            return paramTag.typeExpression.type.kind == SyntaxKind.JSDocOptionalType
          }
        }
      }

      if (hasQuestionToken(node)) {
        return true
      }

      if (node.initializer) {
        val signatureDeclaration = <SignatureDeclaration>node.parent
        val signature = getSignatureFromDeclaration(signatureDeclaration)
        val parameterIndex = ts.indexOf(signatureDeclaration.parameters, node)
        Debug.assert(parameterIndex >= 0)
        return parameterIndex >= signature.minArgumentCount
      }

      return false
    }

    def createTypePredicateFromTypePredicateNode(node: TypePredicateNode): IdentifierTypePredicate | ThisTypePredicate {
      if (node.parameterName.kind == SyntaxKind.Identifier) {
        val parameterName = node.parameterName as Identifier
        return {
          kind: TypePredicateKind.Identifier,
          parameterName: parameterName ? parameterName.text : (),
          parameterIndex: parameterName ? getTypePredicateParameterIndex((node.parent as SignatureDeclaration).parameters, parameterName) : (),
          type: getTypeFromTypeNode(node.type)
        } as IdentifierTypePredicate
      }
      else {
        return {
          kind: TypePredicateKind.This,
          type: getTypeFromTypeNode(node.type)
        } as ThisTypePredicate
      }
    }

    def getSignatureFromDeclaration(declaration: SignatureDeclaration): Signature {
      val links = getNodeLinks(declaration)
      if (!links.resolvedSignature) {
        val classType = declaration.kind == SyntaxKind.Constructor ?
          getDeclaredTypeOfClassOrInterface(getMergedSymbol((<ClassDeclaration>declaration.parent).symbol))
          : ()
        val typeParameters = classType ? classType.localTypeParameters :
          declaration.typeParameters ? getTypeParametersFromDeclaration(declaration.typeParameters) :
          getTypeParametersFromJSDocTemplate(declaration)
        val parameters: Symbol[] = []
        var hasStringLiterals = false
        var minArgumentCount = -1
        val isJSConstructSignature = isJSDocConstructSignature(declaration)
        var returnType: Type = ()

        // If this is a JSDoc construct signature, then skip the first parameter in the
        // parameter list.  The first parameter represents the return type of the construct
        // signature.
        for (var i = isJSConstructSignature ? 1 : 0, n = declaration.parameters.length; i < n; i++) {
          val param = declaration.parameters[i]

          var paramSymbol = param.symbol
          // Include parameter symbol instead of property symbol in the signature
          if (paramSymbol && !!(paramSymbol.flags & SymbolFlags.Property) && !isBindingPattern(param.name)) {
            val resolvedSymbol = resolveName(param, paramSymbol.name, SymbolFlags.Value, (), ())
            paramSymbol = resolvedSymbol
          }
          parameters.push(paramSymbol)

          if (param.type && param.type.kind == SyntaxKind.StringLiteralType) {
            hasStringLiterals = true
          }

          if (param.initializer || param.questionToken || param.dotDotDotToken) {
            if (minArgumentCount < 0) {
              minArgumentCount = i
            }
          }
          else {
            // If we see any required parameters, it means the prior ones were not in fact optional.
            minArgumentCount = -1
          }
        }

        if (minArgumentCount < 0) {
          minArgumentCount = declaration.parameters.length
        }

        if (isJSConstructSignature) {
          minArgumentCount--
          returnType = getTypeFromTypeNode(declaration.parameters[0].type)
        }
        else if (classType) {
          returnType = classType
        }
        else if (declaration.type) {
          returnType = getTypeFromTypeNode(declaration.type)
        }
        else {
          if (declaration.flags & NodeFlags.JavaScriptFile) {
            val type = getReturnTypeFromJSDocComment(declaration)
            if (type && type != unknownType) {
              returnType = type
            }
          }

          // TypeScript 1.0 spec (April 2014):
          // If only one accessor includes a type annotation, the other behaves as if it had the same type annotation.
          if (declaration.kind == SyntaxKind.GetAccessor && !hasDynamicName(declaration)) {
            val setter = <AccessorDeclaration>getDeclarationOfKind(declaration.symbol, SyntaxKind.SetAccessor)
            returnType = getAnnotatedAccessorType(setter)
          }

          if (!returnType && nodeIsMissing((<FunctionLikeDeclaration>declaration).body)) {
            returnType = anyType
          }
        }

        links.resolvedSignature = createSignature(declaration, typeParameters, parameters, returnType, minArgumentCount, hasRestParameter(declaration), hasStringLiterals)
      }
      return links.resolvedSignature
    }

    def getSignaturesOfSymbol(symbol: Symbol): Signature[] {
      if (!symbol) return emptyArray
      val result: Signature[] = []
      for (var i = 0, len = symbol.declarations.length; i < len; i++) {
        val node = symbol.declarations[i]
        switch (node.kind) {
          case SyntaxKind.FunctionType:
          case SyntaxKind.ConstructorType:
          case SyntaxKind.FunctionDeclaration:
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.MethodSignature:
          case SyntaxKind.Constructor:
          case SyntaxKind.CallSignature:
          case SyntaxKind.ConstructSignature:
          case SyntaxKind.IndexSignature:
          case SyntaxKind.GetAccessor:
          case SyntaxKind.SetAccessor:
          case SyntaxKind.FunctionExpression:
          case SyntaxKind.ArrowFunction:
          case SyntaxKind.JSDocFunctionType:
            // Don't include signature if node is the implementation of an overloaded def. A node is considered
            // an implementation node if it has a body and the previous node is of the same kind and immediately
            // precedes the implementation node (i.e. has the same parent and ends where the implementation starts).
            if (i > 0 && (<FunctionLikeDeclaration>node).body) {
              val previous = symbol.declarations[i - 1]
              if (node.parent == previous.parent && node.kind == previous.kind && node.pos == previous.end) {
                break
              }
            }
            result.push(getSignatureFromDeclaration(<SignatureDeclaration>node))
        }
      }
      return result
    }

    def resolveExternalModuleTypeByLiteral(name: StringLiteral) {
      val moduleSym = resolveExternalModuleName(name, name)
      if (moduleSym) {
        val resolvedModuleSymbol = resolveExternalModuleSymbol(moduleSym)
        if (resolvedModuleSymbol) {
          return getTypeOfSymbol(resolvedModuleSymbol)
        }
      }

      return anyType
    }

    def getReturnTypeOfSignature(signature: Signature): Type {
      if (!signature.resolvedReturnType) {
        if (!pushTypeResolution(signature, TypeSystemPropertyName.ResolvedReturnType)) {
          return unknownType
        }
        var type: Type
        if (signature.target) {
          type = instantiateType(getReturnTypeOfSignature(signature.target), signature.mapper)
        }
        else if (signature.unionSignatures) {
          type = getUnionType(map(signature.unionSignatures, getReturnTypeOfSignature))
        }
        else {
          type = getReturnTypeFromBody(<FunctionLikeDeclaration>signature.declaration)
        }
        if (!popTypeResolution()) {
          type = anyType
          if (compilerOptions.noImplicitAny) {
            val declaration = <Declaration>signature.declaration
            if (declaration.name) {
              error(declaration.name, Diagnostics._0_implicitly_has_return_type_any_because_it_does_not_have_a_return_type_annotation_and_is_referenced_directly_or_indirectly_in_one_of_its_return_expressions, declarationNameToString(declaration.name))
            }
            else {
              error(declaration, Diagnostics.Function_implicitly_has_return_type_any_because_it_does_not_have_a_return_type_annotation_and_is_referenced_directly_or_indirectly_in_one_of_its_return_expressions)
            }
          }
        }
        signature.resolvedReturnType = type
      }
      return signature.resolvedReturnType
    }

    def getRestTypeOfSignature(signature: Signature): Type {
      if (signature.hasRestParameter) {
        val type = getTypeOfSymbol(lastOrUndefined(signature.parameters))
        if (type.flags & TypeFlags.Reference && (<TypeReference>type).target == globalArrayType) {
          return (<TypeReference>type).typeArguments[0]
        }
      }
      return anyType
    }

    def getSignatureInstantiation(signature: Signature, typeArguments: Type[]): Signature {
      return instantiateSignature(signature, createTypeMapper(signature.typeParameters, typeArguments), /*eraseTypeParameters*/ true)
    }

    def getErasedSignature(signature: Signature): Signature {
      if (!signature.typeParameters) return signature
      if (!signature.erasedSignatureCache) {
        if (signature.target) {
          signature.erasedSignatureCache = instantiateSignature(getErasedSignature(signature.target), signature.mapper)
        }
        else {
          signature.erasedSignatureCache = instantiateSignature(signature, createTypeEraser(signature.typeParameters), /*eraseTypeParameters*/ true)
        }
      }
      return signature.erasedSignatureCache
    }

    def getOrCreateTypeFromSignature(signature: Signature): ObjectType {
      // There are two ways to declare a construct signature, one is by declaring a class constructor
      // using the constructor keyword, and the other is declaring a bare construct signature in an
      // object type literal or trait (using the new keyword). Each way of declaring a constructor
      // will result in a different declaration kind.
      if (!signature.isolatedSignatureType) {
        val isConstructor = signature.declaration.kind == SyntaxKind.Constructor || signature.declaration.kind == SyntaxKind.ConstructSignature
        val type = <ResolvedType>createObjectType(TypeFlags.Anonymous | TypeFlags.FromSignature)
        type.members = emptySymbols
        type.properties = emptyArray
        type.callSignatures = !isConstructor ? [signature] : emptyArray
        type.constructSignatures = isConstructor ? [signature] : emptyArray
        signature.isolatedSignatureType = type
      }

      return signature.isolatedSignatureType
    }

    def getIndexSymbol(symbol: Symbol): Symbol {
      return symbol.members["__index"]
    }

    def getIndexDeclarationOfSymbol(symbol: Symbol, kind: IndexKind): SignatureDeclaration {
      val syntaxKind = kind == IndexKind.Number ? SyntaxKind.NumberKeyword : SyntaxKind.StringKeyword
      val indexSymbol = getIndexSymbol(symbol)
      if (indexSymbol) {
        for (val decl of indexSymbol.declarations) {
          val node = <SignatureDeclaration>decl
          if (node.parameters.length == 1) {
            val parameter = node.parameters[0]
            if (parameter && parameter.type && parameter.type.kind == syntaxKind) {
              return node
            }
          }
        }
      }

      return ()
    }

    def createIndexInfo(type: Type, isReadonly: Boolean, declaration?: SignatureDeclaration): IndexInfo {
      return { type, isReadonly, declaration }
    }

    def getIndexInfoOfSymbol(symbol: Symbol, kind: IndexKind): IndexInfo {
      val declaration = getIndexDeclarationOfSymbol(symbol, kind)
      if (declaration) {
        return createIndexInfo(declaration.type ? getTypeFromTypeNode(declaration.type) : anyType,
          (declaration.flags & NodeFlags.Readonly) != 0, declaration)
      }
      return ()
    }

    def getConstraintDeclaration(type: TypeParameter) {
      return (<TypeParameterDeclaration>getDeclarationOfKind(type.symbol, SyntaxKind.TypeParameter)).constraint
    }

    def hasConstraintReferenceTo(type: Type, target: TypeParameter): Boolean {
      var checked: Type[]
      while (type && !(type.flags & TypeFlags.ThisType) && type.flags & TypeFlags.TypeParameter && !contains(checked, type)) {
        if (type == target) {
          return true
        }
        (checked || (checked = [])).push(type)
        val constraintDeclaration = getConstraintDeclaration(<TypeParameter>type)
        type = constraintDeclaration && getTypeFromTypeNode(constraintDeclaration)
      }
      return false
    }

    def getConstraintOfTypeParameter(typeParameter: TypeParameter): Type {
      if (!typeParameter.constraint) {
        if (typeParameter.target) {
          val targetConstraint = getConstraintOfTypeParameter(typeParameter.target)
          typeParameter.constraint = targetConstraint ? instantiateType(targetConstraint, typeParameter.mapper) : noConstraintType
        }
        else {
          val constraintDeclaration = getConstraintDeclaration(typeParameter)
          var constraint = getTypeFromTypeNode(constraintDeclaration)
          if (hasConstraintReferenceTo(constraint, typeParameter)) {
            error(constraintDeclaration, Diagnostics.Type_parameter_0_has_a_circular_constraint, typeToString(typeParameter))
            constraint = unknownType
          }
          typeParameter.constraint = constraint
        }
      }
      return typeParameter.constraint == noConstraintType ? () : typeParameter.constraint
    }

    def getParentSymbolOfTypeParameter(typeParameter: TypeParameter): Symbol {
      return getSymbolOfNode(getDeclarationOfKind(typeParameter.symbol, SyntaxKind.TypeParameter).parent)
    }

    def getTypeListId(types: Type[]) {
      if (types) {
        switch (types.length) {
          case 1:
            return "" + types[0].id
          case 2:
            return types[0].id + "," + types[1].id
          default:
            var result = ""
            for (var i = 0; i < types.length; i++) {
              if (i > 0) {
                result += ","
              }
              result += types[i].id
            }
            return result
        }
      }
      return ""
    }

    // This def is used to propagate certain flags when creating new object type references and union types.
    // It is only necessary to do so if a constituent type might be the () type, the null type, the type
    // of an object literal or the anyFunctionType. This is because there are operations in the type checker
    // that care about the presence of such types at arbitrary depth in a containing type.
    def getPropagatingFlagsOfTypes(types: Type[]): TypeFlags {
      var result: TypeFlags = 0
      for (val type of types) {
        result |= type.flags
      }
      return result & TypeFlags.PropagatingFlags
    }

    def createTypeReference(target: GenericType, typeArguments: Type[]): TypeReference {
      val id = getTypeListId(typeArguments)
      var type = target.instantiations[id]
      if (!type) {
        val flags = TypeFlags.Reference | (typeArguments ? getPropagatingFlagsOfTypes(typeArguments) : 0)
        type = target.instantiations[id] = <TypeReference>createObjectType(flags, target.symbol)
        type.target = target
        type.typeArguments = typeArguments
      }
      return type
    }

    // Get type from reference to class or trait
    def getTypeFromClassOrInterfaceReference(node: TypeReferenceNode | ExpressionWithTypeArguments | JSDocTypeReference, symbol: Symbol): Type {
      val type = <InterfaceType>getDeclaredTypeOfSymbol(symbol)
      val typeParameters = type.localTypeParameters
      if (typeParameters) {
        if (!node.typeArguments || node.typeArguments.length != typeParameters.length) {
          error(node, Diagnostics.Generic_type_0_requires_1_type_argument_s, typeToString(type, /*enclosingDeclaration*/ (), TypeFormatFlags.WriteArrayAsGenericType), typeParameters.length)
          return unknownType
        }
        // In a type reference, the outer type parameters of the referenced class or trait are automatically
        // supplied as type arguments and the type reference only specifies arguments for the local type parameters
        // of the class or trait.
        return createTypeReference(<GenericType>type, concatenate(type.outerTypeParameters, map(node.typeArguments, getTypeFromTypeNode)))
      }
      if (node.typeArguments) {
        error(node, Diagnostics.Type_0_is_not_generic, typeToString(type))
        return unknownType
      }
      return type
    }

    // Get type from reference to type alias. When a type alias is generic, the declared type of the type alias may include
    // references to the type parameters of the alias. We replace those with the actual type arguments by instantiating the
    // declared type. Instantiations are cached using the type identities of the type arguments as the key.
    def getTypeFromTypeAliasReference(node: TypeReferenceNode | ExpressionWithTypeArguments | JSDocTypeReference, symbol: Symbol): Type {
      val type = getDeclaredTypeOfSymbol(symbol)
      val links = getSymbolLinks(symbol)
      val typeParameters = links.typeParameters
      if (typeParameters) {
        if (!node.typeArguments || node.typeArguments.length != typeParameters.length) {
          error(node, Diagnostics.Generic_type_0_requires_1_type_argument_s, symbolToString(symbol), typeParameters.length)
          return unknownType
        }
        val typeArguments = map(node.typeArguments, getTypeFromTypeNode)
        val id = getTypeListId(typeArguments)
        return links.instantiations[id] || (links.instantiations[id] = instantiateType(type, createTypeMapper(typeParameters, typeArguments)))
      }
      if (node.typeArguments) {
        error(node, Diagnostics.Type_0_is_not_generic, symbolToString(symbol))
        return unknownType
      }
      return type
    }

    // Get type from reference to named type that cannot be generic (enum or type parameter)
    def getTypeFromNonGenericTypeReference(node: TypeReferenceNode | ExpressionWithTypeArguments | JSDocTypeReference, symbol: Symbol): Type {
      if (node.typeArguments) {
        error(node, Diagnostics.Type_0_is_not_generic, symbolToString(symbol))
        return unknownType
      }
      return getDeclaredTypeOfSymbol(symbol)
    }

    def getTypeReferenceName(node: TypeReferenceNode | ExpressionWithTypeArguments | JSDocTypeReference): LeftHandSideExpression | EntityName {
      switch (node.kind) {
        case SyntaxKind.TypeReference:
          return (<TypeReferenceNode>node).typeName
        case SyntaxKind.JSDocTypeReference:
          return (<JSDocTypeReference>node).name
        case SyntaxKind.ExpressionWithTypeArguments:
          // We only support expressions that are simple qualified names. For other
          // expressions this produces ().
          if (isSupportedExpressionWithTypeArguments(<ExpressionWithTypeArguments>node)) {
            return (<ExpressionWithTypeArguments>node).expression
          }

        // fall through
      }

      return ()
    }

    def resolveTypeReferenceName(
      node: TypeReferenceNode | ExpressionWithTypeArguments | JSDocTypeReference,
      typeReferenceName: LeftHandSideExpression | EntityName) {

      if (!typeReferenceName) {
        return unknownSymbol
      }

      return resolveEntityName(typeReferenceName, SymbolFlags.Type) || unknownSymbol
    }

    def getTypeReferenceType(node: TypeReferenceNode | ExpressionWithTypeArguments | JSDocTypeReference, symbol: Symbol) {
      if (symbol == unknownSymbol) {
        return unknownType
      }

      if (symbol.flags & (SymbolFlags.Class | SymbolFlags.Interface)) {
        return getTypeFromClassOrInterfaceReference(node, symbol)
      }

      if (symbol.flags & SymbolFlags.TypeAlias) {
        return getTypeFromTypeAliasReference(node, symbol)
      }

      if (symbol.flags & SymbolFlags.Value && node.kind == SyntaxKind.JSDocTypeReference) {
        // A JSDocTypeReference may have resolved to a value (as opposed to a type). In
        // that case, the type of this reference is just the type of the value we resolved
        // to.
        return getTypeOfSymbol(symbol)
      }

      return getTypeFromNonGenericTypeReference(node, symbol)
    }

    def getTypeFromTypeReference(node: TypeReferenceNode | ExpressionWithTypeArguments | JSDocTypeReference): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        var symbol: Symbol
        var type: Type
        if (node.kind == SyntaxKind.JSDocTypeReference) {
          val typeReferenceName = getTypeReferenceName(node)
          symbol = resolveTypeReferenceName(node, typeReferenceName)
          type = getTypeReferenceType(node, symbol)

          links.resolvedSymbol = symbol
          links.resolvedType = type
        }
        else {
          // We only support expressions that are simple qualified names. For other expressions this produces ().
          val typeNameOrExpression = node.kind == SyntaxKind.TypeReference ? (<TypeReferenceNode>node).typeName :
            isSupportedExpressionWithTypeArguments(<ExpressionWithTypeArguments>node) ? (<ExpressionWithTypeArguments>node).expression :
              ()
          symbol = typeNameOrExpression && resolveEntityName(typeNameOrExpression, SymbolFlags.Type) || unknownSymbol
          type = symbol == unknownSymbol ? unknownType :
            symbol.flags & (SymbolFlags.Class | SymbolFlags.Interface) ? getTypeFromClassOrInterfaceReference(node, symbol) :
              symbol.flags & SymbolFlags.TypeAlias ? getTypeFromTypeAliasReference(node, symbol) :
                getTypeFromNonGenericTypeReference(node, symbol)
        }
        // Cache both the resolved symbol and the resolved type. The resolved symbol is needed in when we check the
        // type reference in checkTypeReferenceOrExpressionWithTypeArguments.
        links.resolvedSymbol = symbol
        links.resolvedType = type
      }
      return links.resolvedType
    }

    def getTypeFromTypeQueryNode(node: TypeQueryNode): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        // TypeScript 1.0 spec (April 2014): 3.6.3
        // The expression is processed as an identifier expression (section 4.3)
        // or property access expression(section 4.10),
        // the widened type(section 3.9) of which becomes the result.
        links.resolvedType = getWidenedType(checkExpression(node.exprName))
      }
      return links.resolvedType
    }

    def getTypeOfGlobalSymbol(symbol: Symbol, arity: Int): ObjectType {

      def getTypeDeclaration(symbol: Symbol): Declaration {
        val declarations = symbol.declarations
        for (val declaration of declarations) {
          switch (declaration.kind) {
            case SyntaxKind.ClassDeclaration:
            case SyntaxKind.InterfaceDeclaration:
            case SyntaxKind.EnumDeclaration:
              return declaration
          }
        }
      }

      if (!symbol) {
        return arity ? emptyGenericType : emptyObjectType
      }
      val type = getDeclaredTypeOfSymbol(symbol)
      if (!(type.flags & TypeFlags.ObjectType)) {
        error(getTypeDeclaration(symbol), Diagnostics.Global_type_0_must_be_a_class_or_interface_type, symbol.name)
        return arity ? emptyGenericType : emptyObjectType
      }
      if (((<InterfaceType>type).typeParameters ? (<InterfaceType>type).typeParameters.length : 0) != arity) {
        error(getTypeDeclaration(symbol), Diagnostics.Global_type_0_must_have_1_type_parameter_s, symbol.name, arity)
        return arity ? emptyGenericType : emptyObjectType
      }
      return <ObjectType>type
    }

    def getGlobalValueSymbol(name: String): Symbol {
      return getGlobalSymbol(name, SymbolFlags.Value, Diagnostics.Cannot_find_global_value_0)
    }

    def getGlobalTypeSymbol(name: String): Symbol {
      return getGlobalSymbol(name, SymbolFlags.Type, Diagnostics.Cannot_find_global_type_0)
    }

    def getGlobalSymbol(name: String, meaning: SymbolFlags, diagnostic: DiagnosticMessage): Symbol {
      return resolveName((), name, meaning, diagnostic, name)
    }

    def getGlobalType(name: String, arity = 0): ObjectType {
      return getTypeOfGlobalSymbol(getGlobalTypeSymbol(name), arity)
    }

    /**
     * Returns a type that is inside a package at the global scope, e.g.
     * getExportedTypeFromNamespace('JSX', 'Element') returns the JSX.Element type
     */
    def getExportedTypeFromNamespace(package: String, name: String): Type {
      val namespaceSymbol = getGlobalSymbol(package, SymbolFlags.Namespace, /*diagnosticMessage*/ ())
      val typeSymbol = namespaceSymbol && getSymbol(namespaceSymbol.exports, name, SymbolFlags.Type)
      return typeSymbol && getDeclaredTypeOfSymbol(typeSymbol)
    }

    def getGlobalESSymbolConstructorSymbol() {
      return globalESSymbolConstructorSymbol || (globalESSymbolConstructorSymbol = getGlobalValueSymbol("Symbol"))
    }

    /**
      * Creates a TypeReference for a generic `TypedPropertyDescriptor<T>`.
      */
    def createTypedPropertyDescriptorType(propertyType: Type): Type {
      val globalTypedPropertyDescriptorType = getGlobalTypedPropertyDescriptorType()
      return globalTypedPropertyDescriptorType != emptyGenericType
        ? createTypeReference(<GenericType>globalTypedPropertyDescriptorType, [propertyType])
        : emptyObjectType
    }

    /**
     * Instantiates a global type that is generic with some element type, and returns that instantiation.
     */
    def createTypeFromGenericGlobalType(genericGlobalType: GenericType, typeArguments: Type[]): Type {
      return genericGlobalType != emptyGenericType ? createTypeReference(genericGlobalType, typeArguments) : emptyObjectType
    }

    def createIterableType(elementType: Type): Type {
      return createTypeFromGenericGlobalType(globalIterableType, [elementType])
    }

    def createIterableIteratorType(elementType: Type): Type {
      return createTypeFromGenericGlobalType(globalIterableIteratorType, [elementType])
    }

    def createArrayType(elementType: Type): Type {
      return createTypeFromGenericGlobalType(globalArrayType, [elementType])
    }

    def getTypeFromArrayTypeNode(node: ArrayTypeNode): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        links.resolvedType = createArrayType(getTypeFromTypeNode(node.elementType))
      }
      return links.resolvedType
    }

    def createTupleType(elementTypes: Type[]) {
      val id = getTypeListId(elementTypes)
      return tupleTypes[id] || (tupleTypes[id] = createNewTupleType(elementTypes))
    }

    def createNewTupleType(elementTypes: Type[]) {
      val type = <TupleType>createObjectType(TypeFlags.Tuple | getPropagatingFlagsOfTypes(elementTypes))
      type.elementTypes = elementTypes
      return type
    }

    def getTypeFromTupleTypeNode(node: TupleTypeNode): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        links.resolvedType = createTupleType(map(node.elementTypes, getTypeFromTypeNode))
      }
      return links.resolvedType
    }

    def addTypeToSet(typeSet: Type[], type: Type, typeSetKind: TypeFlags) {
      if (type.flags & typeSetKind) {
        addTypesToSet(typeSet, (<UnionOrIntersectionType>type).types, typeSetKind)
      }
      else if (!contains(typeSet, type)) {
        typeSet.push(type)
      }
    }

    // Add the given types to the given type set. Order is preserved, duplicates are removed,
    // and nested types of the given kind are flattened into the set.
    def addTypesToSet(typeSet: Type[], types: Type[], typeSetKind: TypeFlags) {
      for (val type of types) {
        addTypeToSet(typeSet, type, typeSetKind)
      }
    }

    def isSubtypeOfAny(candidate: Type, types: Type[]): Boolean {
      for (var i = 0, len = types.length; i < len; i++) {
        if (candidate != types[i] && isTypeSubtypeOf(candidate, types[i])) {
          return true
        }
      }
      return false
    }

    def removeSubtypes(types: Type[]) {
      var i = types.length
      while (i > 0) {
        i--
        if (isSubtypeOfAny(types[i], types)) {
          types.splice(i, 1)
        }
      }
    }

    def containsTypeAny(types: Type[]): Boolean {
      for (val type of types) {
        if (isTypeAny(type)) {
          return true
        }
      }
      return false
    }

    def removeAllButLast(types: Type[], typeToRemove: Type) {
      var i = types.length
      while (i > 0 && types.length > 1) {
        i--
        if (types[i] == typeToRemove) {
          types.splice(i, 1)
        }
      }
    }

    // We reduce the constituent type set to only include types that aren't subtypes of other types, unless
    // the noSubtypeReduction flag is specified, in which case we perform a simple deduplication based on
    // object identity. Subtype reduction is possible only when union types are known not to circularly
    // reference themselves (as is the case with union types created by expression constructs such as array
    // literals and the || and ?: operators). Named types can circularly reference themselves and therefore
    // cannot be deduplicated during their declaration. For example, "type Item = String | (() => Item" is
    // a named type that circularly references itself.
    def getUnionType(types: Type[], noSubtypeReduction?: Boolean): Type {
      if (types.length == 0) {
        return emptyUnionType
      }
      val typeSet: Type[] = []
      addTypesToSet(typeSet, types, TypeFlags.Union)
      if (containsTypeAny(typeSet)) {
        return anyType
      }
      if (noSubtypeReduction) {
        removeAllButLast(typeSet, undefinedType)
        removeAllButLast(typeSet, nullType)
      }
      else {
        removeSubtypes(typeSet)
      }
      if (typeSet.length == 1) {
        return typeSet[0]
      }
      val id = getTypeListId(typeSet)
      var type = unionTypes[id]
      if (!type) {
        type = unionTypes[id] = <UnionType>createObjectType(TypeFlags.Union | getPropagatingFlagsOfTypes(typeSet))
        type.types = typeSet
      }
      return type
    }

    def getTypeFromUnionTypeNode(node: UnionTypeNode): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        links.resolvedType = getUnionType(map(node.types, getTypeFromTypeNode), /*noSubtypeReduction*/ true)
      }
      return links.resolvedType
    }

    // We do not perform structural deduplication on intersection types. Intersection types are created only by the &
    // type operator and we can't reduce those because we want to support recursive intersection types. For example,
    // a type alias of the form "type List<T> = T & { next: List<T> }" cannot be reduced during its declaration.
    // Also, unlike union types, the order of the constituent types is preserved in order that overload resolution
    // for intersections of types with signatures can be deterministic.
    def getIntersectionType(types: Type[]): Type {
      if (types.length == 0) {
        return emptyObjectType
      }
      val typeSet: Type[] = []
      addTypesToSet(typeSet, types, TypeFlags.Intersection)
      if (containsTypeAny(typeSet)) {
        return anyType
      }
      if (typeSet.length == 1) {
        return typeSet[0]
      }
      val id = getTypeListId(typeSet)
      var type = intersectionTypes[id]
      if (!type) {
        type = intersectionTypes[id] = <IntersectionType>createObjectType(TypeFlags.Intersection | getPropagatingFlagsOfTypes(typeSet))
        type.types = typeSet
      }
      return type
    }

    def getTypeFromIntersectionTypeNode(node: IntersectionTypeNode): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        links.resolvedType = getIntersectionType(map(node.types, getTypeFromTypeNode))
      }
      return links.resolvedType
    }

    def getTypeFromTypeLiteralOrFunctionOrConstructorTypeNode(node: Node): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        // Deferred resolution of members is handled by resolveObjectTypeMembers
        links.resolvedType = createObjectType(TypeFlags.Anonymous, node.symbol)
      }
      return links.resolvedType
    }

    def getStringLiteralTypeForText(text: String): StringLiteralType {
      if (hasProperty(stringLiteralTypes, text)) {
        return stringLiteralTypes[text]
      }

      val type = stringLiteralTypes[text] = <StringLiteralType>createType(TypeFlags.StringLiteral)
      type.text = text
      return type
    }

    def getTypeFromStringLiteralTypeNode(node: StringLiteralTypeNode): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        links.resolvedType = getStringLiteralTypeForText(node.text)
      }
      return links.resolvedType
    }

    def getTypeFromJSDocVariadicType(node: JSDocVariadicType): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        val type = getTypeFromTypeNode(node.type)
        links.resolvedType = type ? createArrayType(type) : unknownType
      }
      return links.resolvedType
    }

    def getTypeFromJSDocTupleType(node: JSDocTupleType): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        val types = map(node.types, getTypeFromTypeNode)
        links.resolvedType = createTupleType(types)
      }
      return links.resolvedType
    }

    def getThisType(node: TypeNode): Type {
      val container = getThisContainer(node, /*includeArrowFunctions*/ false)
      val parent = container && container.parent
      if (parent && (isClassLike(parent) || parent.kind == SyntaxKind.InterfaceDeclaration)) {
        if (!(container.flags & NodeFlags.Static) &&
          (container.kind != SyntaxKind.Constructor || isNodeDescendentOf(node, (<ConstructorDeclaration>container).body))) {
          return getDeclaredTypeOfClassOrInterface(getSymbolOfNode(parent)).thisType
        }
      }
      error(node, Diagnostics.A_this_type_is_available_only_in_a_non_static_member_of_a_class_or_interface)
      return unknownType
    }

    def getTypeFromThisTypeNode(node: TypeNode): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        links.resolvedType = getThisType(node)
      }
      return links.resolvedType
    }

    def getPredicateType(node: TypePredicateNode): Type {
      return createPredicateType(getSymbolOfNode(node), createTypePredicateFromTypePredicateNode(node))
    }

    def createPredicateType(symbol: Symbol, predicate: ThisTypePredicate | IdentifierTypePredicate) {
        val type = createType(TypeFlags.Boolean | TypeFlags.PredicateType) as PredicateType
        type.symbol = symbol
        type.predicate = predicate
        return type
    }

    def getTypeFromPredicateTypeNode(node: TypePredicateNode): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        links.resolvedType = getPredicateType(node)
      }
      return links.resolvedType
    }

    def getTypeFromTypeNode(node: TypeNode): Type {
      switch (node.kind) {
        case SyntaxKind.AnyKeyword:
        case SyntaxKind.JSDocAllType:
        case SyntaxKind.JSDocUnknownType:
          return anyType
        case SyntaxKind.StringKeyword:
          return stringType
        case SyntaxKind.NumberKeyword:
          return numberType
        case SyntaxKind.BooleanKeyword:
          return booleanType
        case SyntaxKind.SymbolKeyword:
          return esSymbolType
        case SyntaxKind.VoidKeyword:
          return voidType
        case SyntaxKind.ThisType:
          return getTypeFromThisTypeNode(node)
        case SyntaxKind.StringLiteralType:
          return getTypeFromStringLiteralTypeNode(<StringLiteralTypeNode>node)
        case SyntaxKind.TypeReference:
        case SyntaxKind.JSDocTypeReference:
          return getTypeFromTypeReference(<TypeReferenceNode>node)
        case SyntaxKind.TypePredicate:
          return getTypeFromPredicateTypeNode(<TypePredicateNode>node)
        case SyntaxKind.ExpressionWithTypeArguments:
          return getTypeFromTypeReference(<ExpressionWithTypeArguments>node)
        case SyntaxKind.TypeQuery:
          return getTypeFromTypeQueryNode(<TypeQueryNode>node)
        case SyntaxKind.ArrayType:
        case SyntaxKind.JSDocArrayType:
          return getTypeFromArrayTypeNode(<ArrayTypeNode>node)
        case SyntaxKind.TupleType:
          return getTypeFromTupleTypeNode(<TupleTypeNode>node)
        case SyntaxKind.UnionType:
        case SyntaxKind.JSDocUnionType:
          return getTypeFromUnionTypeNode(<UnionTypeNode>node)
        case SyntaxKind.IntersectionType:
          return getTypeFromIntersectionTypeNode(<IntersectionTypeNode>node)
        case SyntaxKind.ParenthesizedType:
        case SyntaxKind.JSDocNullableType:
        case SyntaxKind.JSDocNonNullableType:
        case SyntaxKind.JSDocConstructorType:
        case SyntaxKind.JSDocThisType:
        case SyntaxKind.JSDocOptionalType:
          return getTypeFromTypeNode((<ParenthesizedTypeNode | JSDocTypeReferencingNode>node).type)
        case SyntaxKind.FunctionType:
        case SyntaxKind.ConstructorType:
        case SyntaxKind.TypeLiteral:
        case SyntaxKind.JSDocFunctionType:
        case SyntaxKind.JSDocRecordType:
          return getTypeFromTypeLiteralOrFunctionOrConstructorTypeNode(node)
        // This def assumes that an identifier or qualified name is a type expression
        // Callers should first ensure this by calling isTypeNode
        case SyntaxKind.Identifier:
        case SyntaxKind.QualifiedName:
          val symbol = getSymbolAtLocation(node)
          return symbol && getDeclaredTypeOfSymbol(symbol)
        case SyntaxKind.JSDocTupleType:
          return getTypeFromJSDocTupleType(<JSDocTupleType>node)
        case SyntaxKind.JSDocVariadicType:
          return getTypeFromJSDocVariadicType(<JSDocVariadicType>node)
        default:
          return unknownType
      }
    }

    def instantiateList<T>(items: T[], mapper: TypeMapper, instantiator: (item: T, mapper: TypeMapper) => T): T[] {
      if (items && items.length) {
        val result: T[] = []
        for (val v of items) {
          result.push(instantiator(v, mapper))
        }
        return result
      }
      return items
    }

    def createUnaryTypeMapper(source: Type, target: Type): TypeMapper {
      return t => t == source ? target : t
    }

    def createBinaryTypeMapper(source1: Type, target1: Type, source2: Type, target2: Type): TypeMapper {
      return t => t == source1 ? target1 : t == source2 ? target2 : t
    }

    def createTypeMapper(sources: Type[], targets: Type[]): TypeMapper {
      switch (sources.length) {
        case 1: return createUnaryTypeMapper(sources[0], targets[0])
        case 2: return createBinaryTypeMapper(sources[0], targets[0], sources[1], targets[1])
      }
      return t => {
        for (var i = 0; i < sources.length; i++) {
          if (t == sources[i]) {
            return targets[i]
          }
        }
        return t
      }
    }

    def createUnaryTypeEraser(source: Type): TypeMapper {
      return t => t == source ? anyType : t
    }

    def createBinaryTypeEraser(source1: Type, source2: Type): TypeMapper {
      return t => t == source1 || t == source2 ? anyType : t
    }

    def createTypeEraser(sources: Type[]): TypeMapper {
      switch (sources.length) {
        case 1: return createUnaryTypeEraser(sources[0])
        case 2: return createBinaryTypeEraser(sources[0], sources[1])
      }
      return t => {
        for (val source of sources) {
          if (t == source) {
            return anyType
          }
        }
        return t
      }
    }

    def getInferenceMapper(context: InferenceContext): TypeMapper {
      if (!context.mapper) {
        val mapper: TypeMapper = t => {
          val typeParameters = context.typeParameters
          for (var i = 0; i < typeParameters.length; i++) {
            if (t == typeParameters[i]) {
              context.inferences[i].isFixed = true
              return getInferredType(context, i)
            }
          }
          return t
        }
        mapper.context = context
        context.mapper = mapper
      }
      return context.mapper
    }

    def identityMapper(type: Type): Type {
      return type
    }

    def combineTypeMappers(mapper1: TypeMapper, mapper2: TypeMapper): TypeMapper {
      return t => instantiateType(mapper1(t), mapper2)
    }

    def cloneTypeParameter(typeParameter: TypeParameter): TypeParameter {
      val result = <TypeParameter>createType(TypeFlags.TypeParameter)
      result.symbol = typeParameter.symbol
      result.target = typeParameter
      return result
    }

    def cloneTypePredicate(predicate: TypePredicate, mapper: TypeMapper): ThisTypePredicate | IdentifierTypePredicate {
      if (isIdentifierTypePredicate(predicate)) {
        return {
          kind: TypePredicateKind.Identifier,
          parameterName: predicate.parameterName,
          parameterIndex: predicate.parameterIndex,
          type: instantiateType(predicate.type, mapper)
        } as IdentifierTypePredicate
      }
      else {
        return {
          kind: TypePredicateKind.This,
          type: instantiateType(predicate.type, mapper)
        } as ThisTypePredicate
      }
    }

    def instantiateSignature(signature: Signature, mapper: TypeMapper, eraseTypeParameters?: Boolean): Signature {
      var freshTypeParameters: TypeParameter[]
      if (signature.typeParameters && !eraseTypeParameters) {
        // First create a fresh set of type parameters, then include a mapping from the old to the
        // new type parameters in the mapper def. Finally store this mapper in the new type
        // parameters such that we can use it when instantiating constraints.
        freshTypeParameters = map(signature.typeParameters, cloneTypeParameter)
        mapper = combineTypeMappers(createTypeMapper(signature.typeParameters, freshTypeParameters), mapper)
        for (val tp of freshTypeParameters) {
          tp.mapper = mapper
        }
      }
      val result = createSignature(signature.declaration, freshTypeParameters,
        instantiateList(signature.parameters, mapper, instantiateSymbol),
        instantiateType(signature.resolvedReturnType, mapper),
        signature.minArgumentCount, signature.hasRestParameter, signature.hasStringLiterals)
      result.target = signature
      result.mapper = mapper
      return result
    }

    def instantiateSymbol(symbol: Symbol, mapper: TypeMapper): Symbol {
      if (symbol.flags & SymbolFlags.Instantiated) {
        val links = getSymbolLinks(symbol)
        // If symbol being instantiated is itself a instantiation, fetch the original target and combine the
        // type mappers. This ensures that original type identities are properly preserved and that aliases
        // always reference a non-aliases.
        symbol = links.target
        mapper = combineTypeMappers(links.mapper, mapper)
      }

      // Keep the flags from the symbol we're instantiating.  Mark that is instantiated, and
      // also transient so that we can just store data on it directly.
      val result = <TransientSymbol>createSymbol(SymbolFlags.Instantiated | SymbolFlags.Transient | symbol.flags, symbol.name)
      result.declarations = symbol.declarations
      result.parent = symbol.parent
      result.target = symbol
      result.mapper = mapper
      if (symbol.valueDeclaration) {
        result.valueDeclaration = symbol.valueDeclaration
      }

      return result
    }

    def instantiateAnonymousType(type: AnonymousType, mapper: TypeMapper): ObjectType {
      if (mapper.instantiations) {
        val cachedType = mapper.instantiations[type.id]
        if (cachedType) {
          return cachedType
        }
      }
      else {
        mapper.instantiations = []
      }
      // Mark the anonymous type as instantiated such that our infinite instantiation detection logic can recognize it
      val result = <AnonymousType>createObjectType(TypeFlags.Anonymous | TypeFlags.Instantiated, type.symbol)
      result.target = type
      result.mapper = mapper
      mapper.instantiations[type.id] = result
      return result
    }

    def instantiateType(type: Type, mapper: TypeMapper): Type {
      if (type && mapper != identityMapper) {
        if (type.flags & TypeFlags.TypeParameter) {
          return mapper(<TypeParameter>type)
        }
        if (type.flags & TypeFlags.Anonymous) {
          return type.symbol && type.symbol.flags & (SymbolFlags.Function | SymbolFlags.Method | SymbolFlags.Class | SymbolFlags.TypeLiteral | SymbolFlags.ObjectLiteral) ?
            instantiateAnonymousType(<AnonymousType>type, mapper) : type
        }
        if (type.flags & TypeFlags.Reference) {
          return createTypeReference((<TypeReference>type).target, instantiateList((<TypeReference>type).typeArguments, mapper, instantiateType))
        }
        if (type.flags & TypeFlags.Tuple) {
          return createTupleType(instantiateList((<TupleType>type).elementTypes, mapper, instantiateType))
        }
        if (type.flags & TypeFlags.Union) {
          return getUnionType(instantiateList((<UnionType>type).types, mapper, instantiateType), /*noSubtypeReduction*/ true)
        }
        if (type.flags & TypeFlags.Intersection) {
          return getIntersectionType(instantiateList((<IntersectionType>type).types, mapper, instantiateType))
        }
        if (type.flags & TypeFlags.PredicateType) {
          val predicate = (type as PredicateType).predicate
          return createPredicateType(type.symbol, cloneTypePredicate(predicate, mapper))
        }
      }
      return type
    }

    def instantiateIndexInfo(info: IndexInfo, mapper: TypeMapper): IndexInfo {
      return info && createIndexInfo(instantiateType(info.type, mapper), info.isReadonly, info.declaration)
    }

    // Returns true if the given expression contains (at any level of nesting) a def or arrow expression
    // that is subject to contextual typing.
    def isContextSensitive(node: Expression | MethodDeclaration | ObjectLiteralElement): Boolean {
      Debug.assert(node.kind != SyntaxKind.MethodDeclaration || isObjectLiteralMethod(node))
      switch (node.kind) {
        case SyntaxKind.FunctionExpression:
        case SyntaxKind.ArrowFunction:
          return isContextSensitiveFunctionLikeDeclaration(<FunctionExpression>node)
        case SyntaxKind.ObjectLiteralExpression:
          return forEach((<ObjectLiteralExpression>node).properties, isContextSensitive)
        case SyntaxKind.ArrayLiteralExpression:
          return forEach((<ArrayLiteralExpression>node).elements, isContextSensitive)
        case SyntaxKind.ConditionalExpression:
          return isContextSensitive((<ConditionalExpression>node).whenTrue) ||
            isContextSensitive((<ConditionalExpression>node).whenFalse)
        case SyntaxKind.BinaryExpression:
          return (<BinaryExpression>node).operatorToken.kind == SyntaxKind.BarBarToken &&
            (isContextSensitive((<BinaryExpression>node).left) || isContextSensitive((<BinaryExpression>node).right))
        case SyntaxKind.PropertyAssignment:
          return isContextSensitive((<PropertyAssignment>node).initializer)
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.MethodSignature:
          return isContextSensitiveFunctionLikeDeclaration(<MethodDeclaration>node)
        case SyntaxKind.ParenthesizedExpression:
          return isContextSensitive((<ParenthesizedExpression>node).expression)
      }

      return false
    }

    def isContextSensitiveFunctionLikeDeclaration(node: FunctionLikeDeclaration) {
      return !node.typeParameters && node.parameters.length && !forEach(node.parameters, p => p.type)
    }

    def getTypeWithoutSignatures(type: Type): Type {
      if (type.flags & TypeFlags.ObjectType) {
        val resolved = resolveStructuredTypeMembers(<ObjectType>type)
        if (resolved.constructSignatures.length) {
          val result = <ResolvedType>createObjectType(TypeFlags.Anonymous, type.symbol)
          result.members = resolved.members
          result.properties = resolved.properties
          result.callSignatures = emptyArray
          result.constructSignatures = emptyArray
          type = result
        }
      }
      return type
    }

    // TYPE CHECKING

    def isTypeIdenticalTo(source: Type, target: Type): Boolean {
      return checkTypeRelatedTo(source, target, identityRelation, /*errorNode*/ ())
    }

    def compareTypesIdentical(source: Type, target: Type): Ternary {
      return checkTypeRelatedTo(source, target, identityRelation, /*errorNode*/ ()) ? Ternary.True : Ternary.False
    }

    def compareTypesAssignable(source: Type, target: Type): Ternary {
      return checkTypeRelatedTo(source, target, assignableRelation, /*errorNode*/ ()) ? Ternary.True : Ternary.False
    }

    def isTypeSubtypeOf(source: Type, target: Type): Boolean {
      return checkTypeSubtypeOf(source, target, /*errorNode*/ ())
    }

    def isTypeAssignableTo(source: Type, target: Type): Boolean {
      return checkTypeAssignableTo(source, target, /*errorNode*/ ())
    }

    def checkTypeSubtypeOf(source: Type, target: Type, errorNode: Node, headMessage?: DiagnosticMessage, containingMessageChain?: DiagnosticMessageChain): Boolean {
      return checkTypeRelatedTo(source, target, subtypeRelation, errorNode, headMessage, containingMessageChain)
    }

    def checkTypeAssignableTo(source: Type, target: Type, errorNode: Node, headMessage?: DiagnosticMessage, containingMessageChain?: DiagnosticMessageChain): Boolean {
      return checkTypeRelatedTo(source, target, assignableRelation, errorNode, headMessage, containingMessageChain)
    }

    def isSignatureAssignableTo(source: Signature,
                     target: Signature,
                     ignoreReturnTypes: Boolean): Boolean {
      return compareSignaturesRelated(source, target, ignoreReturnTypes, /*reportErrors*/ false, /*errorReporter*/ (), compareTypesAssignable) != Ternary.False
    }

    /**
     * See signatureRelatedTo, compareSignaturesIdentical
     */
    def compareSignaturesRelated(source: Signature,
                      target: Signature,
                      ignoreReturnTypes: Boolean,
                      reportErrors: Boolean,
                      errorReporter: (d: DiagnosticMessage, arg0?: String, arg1?: String) => Unit,
                      compareTypes: (s: Type, t: Type, reportErrors?: Boolean) => Ternary): Ternary {
      // TODO (drosen): De-duplicate code between related functions.
      if (source == target) {
        return Ternary.True
      }
      if (!target.hasRestParameter && source.minArgumentCount > target.parameters.length) {
        return Ternary.False
      }

      // Spec 1.0 Section 3.8.3 & 3.8.4:
      // M and N (the signatures) are instantiated using type Any as the type argument for all type parameters declared by M and N
      source = getErasedSignature(source)
      target = getErasedSignature(target)

      var result = Ternary.True

      val sourceMax = getNumNonRestParameters(source)
      val targetMax = getNumNonRestParameters(target)
      val checkCount = getNumParametersToCheckForSignatureRelatability(source, sourceMax, target, targetMax)
      val sourceParams = source.parameters
      val targetParams = target.parameters
      for (var i = 0; i < checkCount; i++) {
        val s = i < sourceMax ? getTypeOfSymbol(sourceParams[i]) : getRestTypeOfSignature(source)
        val t = i < targetMax ? getTypeOfSymbol(targetParams[i]) : getRestTypeOfSignature(target)
        val related = compareTypes(t, s, /*reportErrors*/ false) || compareTypes(s, t, reportErrors)
        if (!related) {
          if (reportErrors) {
            errorReporter(Diagnostics.Types_of_parameters_0_and_1_are_incompatible,
              sourceParams[i < sourceMax ? i : sourceMax].name,
              targetParams[i < targetMax ? i : targetMax].name)
          }
          return Ternary.False
        }
        result &= related
      }

      if (!ignoreReturnTypes) {
        val targetReturnType = getReturnTypeOfSignature(target)
        if (targetReturnType == voidType) {
          return result
        }
        val sourceReturnType = getReturnTypeOfSignature(source)

        // The following block preserves behavior forbidding Boolean returning functions from being assignable to type guard returning functions
        if (targetReturnType.flags & TypeFlags.PredicateType && (targetReturnType as PredicateType).predicate.kind == TypePredicateKind.Identifier) {
          if (!(sourceReturnType.flags & TypeFlags.PredicateType)) {
            if (reportErrors) {
              errorReporter(Diagnostics.Signature_0_must_have_a_type_predicate, signatureToString(source))
            }
            return Ternary.False
          }
        }

        result &= compareTypes(sourceReturnType, targetReturnType, reportErrors)
      }

      return result
    }

    def isImplementationCompatibleWithOverload(implementation: Signature, overload: Signature): Boolean {
      val erasedSource = getErasedSignature(implementation)
      val erasedTarget = getErasedSignature(overload)

      // First see if the return types are compatible in either direction.
      val sourceReturnType = getReturnTypeOfSignature(erasedSource)
      val targetReturnType = getReturnTypeOfSignature(erasedTarget)
      if (targetReturnType == voidType
        || checkTypeRelatedTo(targetReturnType, sourceReturnType, assignableRelation, /*errorNode*/ ())
        || checkTypeRelatedTo(sourceReturnType, targetReturnType, assignableRelation, /*errorNode*/ ())) {

        return isSignatureAssignableTo(erasedSource, erasedTarget, /*ignoreReturnTypes*/ true)
      }

      return false
    }

    def getNumNonRestParameters(signature: Signature) {
      val numParams = signature.parameters.length
      return signature.hasRestParameter ?
        numParams - 1 :
        numParams
    }

    def getNumParametersToCheckForSignatureRelatability(source: Signature, sourceNonRestParamCount: Int, target: Signature, targetNonRestParamCount: Int) {
      if (source.hasRestParameter == target.hasRestParameter) {
        if (source.hasRestParameter) {
          // If both have rest parameters, get the max and add 1 to
          // compensate for the rest parameter.
          return Math.max(sourceNonRestParamCount, targetNonRestParamCount) + 1
        }
        else {
          return Math.min(sourceNonRestParamCount, targetNonRestParamCount)
        }
      }
      else {
        // Return the count for whichever signature doesn't have rest parameters.
        return source.hasRestParameter ?
          targetNonRestParamCount :
          sourceNonRestParamCount
      }
    }

    /**
     * Checks if 'source' is related to 'target' (e.g.: is a assignable to).
     * @param source The left-hand-side of the relation.
     * @param target The right-hand-side of the relation.
     * @param relation The relation considered. One of 'identityRelation', 'assignableRelation', or 'subTypeRelation'.
     * Used as both to determine which checks are performed and as a cache of previously computed results.
     * @param errorNode The suggested node upon which all errors will be reported, if defined. This may or may not be the actual node used.
     * @param headMessage If the error chain should be prepended by a head message, then headMessage will be used.
     * @param containingMessageChain A chain of errors to prepend any new errors found.
     */
    def checkTypeRelatedTo(
      source: Type,
      target: Type,
      relation: Map<RelationComparisonResult>,
      errorNode: Node,
      headMessage?: DiagnosticMessage,
      containingMessageChain?: DiagnosticMessageChain): Boolean {

      var errorInfo: DiagnosticMessageChain
      var sourceStack: ObjectType[]
      var targetStack: ObjectType[]
      var maybeStack: Map<RelationComparisonResult>[]
      var expandingFlags: Int
      var depth = 0
      var overflow = false

      Debug.assert(relation != identityRelation || !errorNode, "no error reporting in identity checking")

      val result = isRelatedTo(source, target, /*reportErrors*/ !!errorNode, headMessage)
      if (overflow) {
        error(errorNode, Diagnostics.Excessive_stack_depth_comparing_types_0_and_1, typeToString(source), typeToString(target))
      }
      else if (errorInfo) {
        if (containingMessageChain) {
          errorInfo = concatenateDiagnosticMessageChains(containingMessageChain, errorInfo)
        }

        diagnostics.add(createDiagnosticForNodeFromMessageChain(errorNode, errorInfo))
      }
      return result != Ternary.False

      def reportError(message: DiagnosticMessage, arg0?: String, arg1?: String, arg2?: String): Unit {
        Debug.assert(!!errorNode)
        errorInfo = chainDiagnosticMessages(errorInfo, message, arg0, arg1, arg2)
      }

      def reportRelationError(message: DiagnosticMessage, source: Type, target: Type) {
        var sourceType = typeToString(source)
        var targetType = typeToString(target)
        if (sourceType == targetType) {
          sourceType = typeToString(source, /*enclosingDeclaration*/ (), TypeFormatFlags.UseFullyQualifiedType)
          targetType = typeToString(target, /*enclosingDeclaration*/ (), TypeFormatFlags.UseFullyQualifiedType)
        }
        reportError(message || Diagnostics.Type_0_is_not_assignable_to_type_1, sourceType, targetType)
      }

      // Compare two types and return
      // Ternary.True if they are related with no assumptions,
      // Ternary.Maybe if they are related with assumptions of other relationships, or
      // Ternary.False if they are not related.
      def isRelatedTo(source: Type, target: Type, reportErrors?: Boolean, headMessage?: DiagnosticMessage): Ternary {
        var result: Ternary
        // both types are the same - covers 'they are the same primitive type or both are Any' or the same type parameter cases
        if (source == target) return Ternary.True
        if (relation == identityRelation) {
          return isIdenticalTo(source, target)
        }

        if (isTypeAny(target)) return Ternary.True
        if (source == undefinedType) return Ternary.True
        if (source == nullType && target != undefinedType) return Ternary.True
        if (source.flags & TypeFlags.Enum && target == numberType) return Ternary.True
        if (source.flags & TypeFlags.Enum && target.flags & TypeFlags.Enum) {
          if (result = enumRelatedTo(source, target)) {
            return result
          }
        }
        if (source.flags & TypeFlags.StringLiteral && target == stringType) return Ternary.True
        if (relation == assignableRelation) {
          if (isTypeAny(source)) return Ternary.True
          if (source == numberType && target.flags & TypeFlags.Enum) return Ternary.True
        }
        if (source.flags & TypeFlags.Boolean && target.flags & TypeFlags.Boolean) {
          if (source.flags & TypeFlags.PredicateType && target.flags & TypeFlags.PredicateType) {
            val sourcePredicate = source as PredicateType
            val targetPredicate = target as PredicateType
            if (sourcePredicate.predicate.kind != targetPredicate.predicate.kind) {
              if (reportErrors) {
                reportError(Diagnostics.A_this_based_type_guard_is_not_compatible_with_a_parameter_based_type_guard)
                reportError(Diagnostics.Type_predicate_0_is_not_assignable_to_1, typeToString(source), typeToString(target))
              }
              return Ternary.False
            }
            if (sourcePredicate.predicate.kind == TypePredicateKind.Identifier) {
              val sourceIdentifierPredicate = sourcePredicate.predicate as IdentifierTypePredicate
              val targetIdentifierPredicate = targetPredicate.predicate as IdentifierTypePredicate
              if (sourceIdentifierPredicate.parameterIndex != targetIdentifierPredicate.parameterIndex) {
                if (reportErrors) {
                  reportError(Diagnostics.Parameter_0_is_not_in_the_same_position_as_parameter_1, sourceIdentifierPredicate.parameterName, targetIdentifierPredicate.parameterName)
                  reportError(Diagnostics.Type_predicate_0_is_not_assignable_to_1, typeToString(source), typeToString(target))
                }
                return Ternary.False
              }
            }
            val related = isRelatedTo(sourcePredicate.predicate.type, targetPredicate.predicate.type, reportErrors, headMessage)
            if (related == Ternary.False && reportErrors) {
              reportError(Diagnostics.Type_predicate_0_is_not_assignable_to_1, typeToString(source), typeToString(target))
            }
            return related
          }
          return Ternary.True
        }

        if (source.flags & TypeFlags.FreshObjectLiteral) {
          if (hasExcessProperties(<FreshObjectLiteralType>source, target, reportErrors)) {
            if (reportErrors) {
              reportRelationError(headMessage, source, target)
            }
            return Ternary.False
          }
          // Above we check for excess properties with respect to the entire target type. When union
          // and intersection types are further deconstructed on the target side, we don't want to
          // make the check again (as it might fail for a partial target type). Therefore we obtain
          // the regular source type and proceed with that.
          if (target.flags & TypeFlags.UnionOrIntersection) {
            source = getRegularTypeOfObjectLiteral(source)
          }
        }

        val saveErrorInfo = errorInfo

        // Note that the "each" checks must precede the "some" checks to produce the correct results
        if (source.flags & TypeFlags.Union) {
          if (result = eachTypeRelatedToType(<UnionType>source, target, reportErrors)) {
            return result
          }
        }
        else if (target.flags & TypeFlags.Intersection) {
          if (result = typeRelatedToEachType(source, <IntersectionType>target, reportErrors)) {
            return result
          }
        }
        else {
          // It is necessary to try "some" checks on both sides because there may be nested "each" checks
          // on either side that need to be prioritized. For example, A | B = (A | B) & (C | D) or
          // A & B = (A & B) | (C & D).
          if (source.flags & TypeFlags.Intersection) {
            // If target is a union type the following check will report errors so we suppress them here
            if (result = someTypeRelatedToType(<IntersectionType>source, target, reportErrors && !(target.flags & TypeFlags.Union))) {
              return result
            }
          }
          if (target.flags & TypeFlags.Union) {
            if (result = typeRelatedToSomeType(source, <UnionType>target, reportErrors)) {
              return result
            }
          }
        }

        if (source.flags & TypeFlags.TypeParameter) {
          var constraint = getConstraintOfTypeParameter(<TypeParameter>source)
          if (!constraint || constraint.flags & TypeFlags.Any) {
            constraint = emptyObjectType
          }
          // Report constraint errors only if the constraint is not the empty object type
          val reportConstraintErrors = reportErrors && constraint != emptyObjectType
          if (result = isRelatedTo(constraint, target, reportConstraintErrors)) {
            errorInfo = saveErrorInfo
            return result
          }
        }
        else {
          if (source.flags & TypeFlags.Reference && target.flags & TypeFlags.Reference && (<TypeReference>source).target == (<TypeReference>target).target) {
            // We have type references to same target type, see if relationship holds for all type arguments
            if (result = typeArgumentsRelatedTo(<TypeReference>source, <TypeReference>target, reportErrors)) {
              return result
            }
          }
          // Even if relationship doesn't hold for unions, intersections, or generic type references,
          // it may hold in a structural comparison.
          val apparentSource = getApparentType(source)
          // In a check of the form X = A & B, we will have previously checked if A relates to X or B relates
          // to X. Failing both of those we want to check if the aggregation of A and B's members structurally
          // relates to X. Thus, we include intersection types on the source side here.
          if (apparentSource.flags & (TypeFlags.ObjectType | TypeFlags.Intersection) && target.flags & TypeFlags.ObjectType) {
            // Report structural errors only if we haven't reported any errors yet
            val reportStructuralErrors = reportErrors && errorInfo == saveErrorInfo && !(source.flags & TypeFlags.Primitive)
            if (result = objectTypeRelatedTo(apparentSource, source, target, reportStructuralErrors)) {
              errorInfo = saveErrorInfo
              return result
            }
          }
        }

        if (reportErrors) {
          reportRelationError(headMessage, source, target)
        }
        return Ternary.False
      }

      def isIdenticalTo(source: Type, target: Type): Ternary {
        var result: Ternary
        if (source.flags & TypeFlags.ObjectType && target.flags & TypeFlags.ObjectType) {
          if (source.flags & TypeFlags.Reference && target.flags & TypeFlags.Reference && (<TypeReference>source).target == (<TypeReference>target).target) {
            // We have type references to same target type, see if all type arguments are identical
            if (result = typeArgumentsRelatedTo(<TypeReference>source, <TypeReference>target, /*reportErrors*/ false)) {
              return result
            }
          }
          return objectTypeRelatedTo(source, source, target, /*reportErrors*/ false)
        }
        if (source.flags & TypeFlags.Union && target.flags & TypeFlags.Union ||
          source.flags & TypeFlags.Intersection && target.flags & TypeFlags.Intersection) {
          if (result = eachTypeRelatedToSomeType(<UnionOrIntersectionType>source, <UnionOrIntersectionType>target)) {
            if (result &= eachTypeRelatedToSomeType(<UnionOrIntersectionType>target, <UnionOrIntersectionType>source)) {
              return result
            }
          }
        }
        return Ternary.False
      }

      // Check if a property with the given name is known anywhere in the given type. In an object type, a property
      // is considered known if the object type is empty and the check is for assignability, if the object type has
      // index signatures, or if the property is actually declared in the object type. In a union or intersection
      // type, a property is considered known if it is known in any constituent type.
      def isKnownProperty(type: Type, name: String): Boolean {
        if (type.flags & TypeFlags.ObjectType) {
          val resolved = resolveStructuredTypeMembers(type)
          if (relation == assignableRelation && (type == globalObjectType || resolved.properties.length == 0) ||
            resolved.stringIndexInfo || resolved.numberIndexInfo || getPropertyOfType(type, name)) {
            return true
          }
        }
        else if (type.flags & TypeFlags.UnionOrIntersection) {
          for (val t of (<UnionOrIntersectionType>type).types) {
            if (isKnownProperty(t, name)) {
              return true
            }
          }
        }
        return false
      }

      def hasExcessProperties(source: FreshObjectLiteralType, target: Type, reportErrors: Boolean): Boolean {
        if (!(target.flags & TypeFlags.ObjectLiteralPatternWithComputedProperties) && maybeTypeOfKind(target, TypeFlags.ObjectType)) {
          for (val prop of getPropertiesOfObjectType(source)) {
            if (!isKnownProperty(target, prop.name)) {
              if (reportErrors) {
                // We know *exactly* where things went wrong when comparing the types.
                // Use this property as the error node as this will be more helpful in
                // reasoning about what went wrong.
                Debug.assert(!!errorNode)
                errorNode = prop.valueDeclaration
                reportError(Diagnostics.Object_literal_may_only_specify_known_properties_and_0_does_not_exist_in_type_1,
                  symbolToString(prop), typeToString(target))
              }
              return true
            }
          }
        }
        return false
      }

      def eachTypeRelatedToSomeType(source: UnionOrIntersectionType, target: UnionOrIntersectionType): Ternary {
        var result = Ternary.True
        val sourceTypes = source.types
        for (val sourceType of sourceTypes) {
          val related = typeRelatedToSomeType(sourceType, target, /*reportErrors*/ false)
          if (!related) {
            return Ternary.False
          }
          result &= related
        }
        return result
      }

      def typeRelatedToSomeType(source: Type, target: UnionOrIntersectionType, reportErrors: Boolean): Ternary {
        val targetTypes = target.types
        for (var i = 0, len = targetTypes.length; i < len; i++) {
          val related = isRelatedTo(source, targetTypes[i], reportErrors && i == len - 1)
          if (related) {
            return related
          }
        }
        return Ternary.False
      }

      def typeRelatedToEachType(source: Type, target: UnionOrIntersectionType, reportErrors: Boolean): Ternary {
        var result = Ternary.True
        val targetTypes = target.types
        for (val targetType of targetTypes) {
          val related = isRelatedTo(source, targetType, reportErrors)
          if (!related) {
            return Ternary.False
          }
          result &= related
        }
        return result
      }

      def someTypeRelatedToType(source: UnionOrIntersectionType, target: Type, reportErrors: Boolean): Ternary {
        val sourceTypes = source.types
        for (var i = 0, len = sourceTypes.length; i < len; i++) {
          val related = isRelatedTo(sourceTypes[i], target, reportErrors && i == len - 1)
          if (related) {
            return related
          }
        }
        return Ternary.False
      }

      def eachTypeRelatedToType(source: UnionOrIntersectionType, target: Type, reportErrors: Boolean): Ternary {
        var result = Ternary.True
        val sourceTypes = source.types
        for (val sourceType of sourceTypes) {
          val related = isRelatedTo(sourceType, target, reportErrors)
          if (!related) {
            return Ternary.False
          }
          result &= related
        }
        return result
      }

      def typeArgumentsRelatedTo(source: TypeReference, target: TypeReference, reportErrors: Boolean): Ternary {
        val sources = source.typeArguments || emptyArray
        val targets = target.typeArguments || emptyArray
        if (sources.length != targets.length && relation == identityRelation) {
          return Ternary.False
        }
        val length = sources.length <= targets.length ? sources.length : targets.length
        var result = Ternary.True
        for (var i = 0; i < length; i++) {
          val related = isRelatedTo(sources[i], targets[i], reportErrors)
          if (!related) {
            return Ternary.False
          }
          result &= related
        }
        return result
      }

      // Determine if two object types are related by structure. First, check if the result is already available in the global cache.
      // Second, check if we have already started a comparison of the given two types in which case we assume the result to be true.
      // Third, check if both types are part of deeply nested chains of generic type instantiations and if so assume the types are
      // equal and infinitely expanding. Fourth, if we have reached a depth of 100 nested comparisons, assume we have runaway recursion
      // and issue an error. Otherwise, actually compare the structure of the two types.
      def objectTypeRelatedTo(source: Type, originalSource: Type, target: Type, reportErrors: Boolean): Ternary {
        if (overflow) {
          return Ternary.False
        }
        val id = relation != identityRelation || source.id < target.id ? source.id + "," + target.id : target.id + "," + source.id
        val related = relation[id]
        if (related != ()) {
          if (reportErrors && related == RelationComparisonResult.Failed) {
            // We are elaborating errors and the cached result is an unreported failure. Record the result as a reported
            // failure and continue computing the relation such that errors get reported.
            relation[id] = RelationComparisonResult.FailedAndReported
          }
          else {
            return related == RelationComparisonResult.Succeeded ? Ternary.True : Ternary.False
          }
        }
        if (depth > 0) {
          for (var i = 0; i < depth; i++) {
            // If source and target are already being compared, consider them related with assumptions
            if (maybeStack[i][id]) {
              return Ternary.Maybe
            }
          }
          if (depth == 100) {
            overflow = true
            return Ternary.False
          }
        }
        else {
          sourceStack = []
          targetStack = []
          maybeStack = []
          expandingFlags = 0
        }
        sourceStack[depth] = source
        targetStack[depth] = target
        maybeStack[depth] = {}
        maybeStack[depth][id] = RelationComparisonResult.Succeeded
        depth++
        val saveExpandingFlags = expandingFlags
        if (!(expandingFlags & 1) && isDeeplyNestedGeneric(source, sourceStack, depth)) expandingFlags |= 1
        if (!(expandingFlags & 2) && isDeeplyNestedGeneric(target, targetStack, depth)) expandingFlags |= 2
        var result: Ternary
        if (expandingFlags == 3) {
          result = Ternary.Maybe
        }
        else {
          result = propertiesRelatedTo(source, target, reportErrors)
          if (result) {
            result &= signaturesRelatedTo(source, target, SignatureKind.Call, reportErrors)
            if (result) {
              result &= signaturesRelatedTo(source, target, SignatureKind.Construct, reportErrors)
              if (result) {
                result &= stringIndexTypesRelatedTo(source, originalSource, target, reportErrors)
                if (result) {
                  result &= numberIndexTypesRelatedTo(source, originalSource, target, reportErrors)
                }
              }
            }
          }
        }
        expandingFlags = saveExpandingFlags
        depth--
        if (result) {
          val maybeCache = maybeStack[depth]
          // If result is definitely true, copy assumptions to global cache, else copy to next level up
          val destinationCache = (result == Ternary.True || depth == 0) ? relation : maybeStack[depth - 1]
          copyMap(maybeCache, destinationCache)
        }
        else {
          // A false result goes straight into global cache (when something is false under assumptions it
          // will also be false without assumptions)
          relation[id] = reportErrors ? RelationComparisonResult.FailedAndReported : RelationComparisonResult.Failed
        }
        return result
      }

      def propertiesRelatedTo(source: Type, target: Type, reportErrors: Boolean): Ternary {
        if (relation == identityRelation) {
          return propertiesIdenticalTo(source, target)
        }
        var result = Ternary.True
        val properties = getPropertiesOfObjectType(target)
        val requireOptionalProperties = relation == subtypeRelation && !(source.flags & TypeFlags.ObjectLiteral)
        for (val targetProp of properties) {
          val sourceProp = getPropertyOfType(source, targetProp.name)

          if (sourceProp != targetProp) {
            if (!sourceProp) {
              if (!(targetProp.flags & SymbolFlags.Optional) || requireOptionalProperties) {
                if (reportErrors) {
                  reportError(Diagnostics.Property_0_is_missing_in_type_1, symbolToString(targetProp), typeToString(source))
                }
                return Ternary.False
              }
            }
            else if (!(targetProp.flags & SymbolFlags.Prototype)) {
              val sourcePropFlags = getDeclarationFlagsFromSymbol(sourceProp)
              val targetPropFlags = getDeclarationFlagsFromSymbol(targetProp)
              if (sourcePropFlags & NodeFlags.Private || targetPropFlags & NodeFlags.Private) {
                if (sourceProp.valueDeclaration != targetProp.valueDeclaration) {
                  if (reportErrors) {
                    if (sourcePropFlags & NodeFlags.Private && targetPropFlags & NodeFlags.Private) {
                      reportError(Diagnostics.Types_have_separate_declarations_of_a_private_property_0, symbolToString(targetProp))
                    }
                    else {
                      reportError(Diagnostics.Property_0_is_private_in_type_1_but_not_in_type_2, symbolToString(targetProp),
                        typeToString(sourcePropFlags & NodeFlags.Private ? source : target),
                        typeToString(sourcePropFlags & NodeFlags.Private ? target : source))
                    }
                  }
                  return Ternary.False
                }
              }
              else if (targetPropFlags & NodeFlags.Protected) {
                val sourceDeclaredInClass = sourceProp.parent && sourceProp.parent.flags & SymbolFlags.Class
                val sourceClass = sourceDeclaredInClass ? <InterfaceType>getDeclaredTypeOfSymbol(getParentOfSymbol(sourceProp)) : ()
                val targetClass = <InterfaceType>getDeclaredTypeOfSymbol(getParentOfSymbol(targetProp))
                if (!sourceClass || !hasBaseType(sourceClass, targetClass)) {
                  if (reportErrors) {
                    reportError(Diagnostics.Property_0_is_protected_but_type_1_is_not_a_class_derived_from_2,
                      symbolToString(targetProp), typeToString(sourceClass || source), typeToString(targetClass))
                  }
                  return Ternary.False
                }
              }
              else if (sourcePropFlags & NodeFlags.Protected) {
                if (reportErrors) {
                  reportError(Diagnostics.Property_0_is_protected_in_type_1_but_public_in_type_2,
                    symbolToString(targetProp), typeToString(source), typeToString(target))
                }
                return Ternary.False
              }
              val related = isRelatedTo(getTypeOfSymbol(sourceProp), getTypeOfSymbol(targetProp), reportErrors)
              if (!related) {
                if (reportErrors) {
                  reportError(Diagnostics.Types_of_property_0_are_incompatible, symbolToString(targetProp))
                }
                return Ternary.False
              }
              result &= related
              if (sourceProp.flags & SymbolFlags.Optional && !(targetProp.flags & SymbolFlags.Optional)) {
                // TypeScript 1.0 spec (April 2014): 3.8.3
                // S is a subtype of a type T, and T is a supertype of S if ...
                // S' and T are object types and, for each member M in T..
                // M is a property and S' contains a property N where
                // if M is a required property, N is also a required property
                // (M - property in T)
                // (N - property in S)
                if (reportErrors) {
                  reportError(Diagnostics.Property_0_is_optional_in_type_1_but_required_in_type_2,
                    symbolToString(targetProp), typeToString(source), typeToString(target))
                }
                return Ternary.False
              }
            }
          }
        }
        return result
      }

      def propertiesIdenticalTo(source: Type, target: Type): Ternary {
        if (!(source.flags & TypeFlags.ObjectType && target.flags & TypeFlags.ObjectType)) {
          return Ternary.False
        }
        val sourceProperties = getPropertiesOfObjectType(source)
        val targetProperties = getPropertiesOfObjectType(target)
        if (sourceProperties.length != targetProperties.length) {
          return Ternary.False
        }
        var result = Ternary.True
        for (val sourceProp of sourceProperties) {
          val targetProp = getPropertyOfObjectType(target, sourceProp.name)
          if (!targetProp) {
            return Ternary.False
          }
          val related = compareProperties(sourceProp, targetProp, isRelatedTo)
          if (!related) {
            return Ternary.False
          }
          result &= related
        }
        return result
      }

      def signaturesRelatedTo(source: Type, target: Type, kind: SignatureKind, reportErrors: Boolean): Ternary {
        if (relation == identityRelation) {
          return signaturesIdenticalTo(source, target, kind)
        }
        if (target == anyFunctionType || source == anyFunctionType) {
          return Ternary.True
        }

        val sourceSignatures = getSignaturesOfType(source, kind)
        val targetSignatures = getSignaturesOfType(target, kind)
        if (kind == SignatureKind.Construct && sourceSignatures.length && targetSignatures.length &&
          isAbstractConstructorType(source) && !isAbstractConstructorType(target)) {
          // An abstract constructor type is not assignable to a non-abstract constructor type
          // as it would otherwise be possible to new an abstract class. Note that the assignability
          // check we perform for an extends clause excludes construct signatures from the target,
          // so this check never proceeds.
          if (reportErrors) {
            reportError(Diagnostics.Cannot_assign_an_abstract_constructor_type_to_a_non_abstract_constructor_type)
          }
          return Ternary.False
        }

        var result = Ternary.True
        val saveErrorInfo = errorInfo

        outer: for (val t of targetSignatures) {
          // Only elaborate errors from the first failure
          var shouldElaborateErrors = reportErrors
          for (val s of sourceSignatures) {
            val related = signatureRelatedTo(s, t, shouldElaborateErrors)
            if (related) {
              result &= related
              errorInfo = saveErrorInfo
              continue outer
            }
            shouldElaborateErrors = false
          }

          if (shouldElaborateErrors) {
            reportError(Diagnostics.Type_0_provides_no_match_for_the_signature_1,
              typeToString(source),
              signatureToString(t, /*enclosingDeclaration*/ (), /*flags*/ (), kind))
          }
          return Ternary.False
        }
        return result
      }

      /**
       * See signatureAssignableTo, compareSignaturesIdentical
       */
      def signatureRelatedTo(source: Signature, target: Signature, reportErrors: Boolean): Ternary {
        return compareSignaturesRelated(source, target, /*ignoreReturnTypes*/ false, reportErrors, reportError, isRelatedTo)
      }

      def signaturesIdenticalTo(source: Type, target: Type, kind: SignatureKind): Ternary {
        val sourceSignatures = getSignaturesOfType(source, kind)
        val targetSignatures = getSignaturesOfType(target, kind)
        if (sourceSignatures.length != targetSignatures.length) {
          return Ternary.False
        }
        var result = Ternary.True
        for (var i = 0, len = sourceSignatures.length; i < len; i++) {
          val related = compareSignaturesIdentical(sourceSignatures[i], targetSignatures[i], /*partialMatch*/ false, /*ignoreReturnTypes*/ false, isRelatedTo)
          if (!related) {
            return Ternary.False
          }
          result &= related
        }
        return result
      }

      def stringIndexTypesRelatedTo(source: Type, originalSource: Type, target: Type, reportErrors: Boolean): Ternary {
        if (relation == identityRelation) {
          return indexTypesIdenticalTo(IndexKind.String, source, target)
        }
        val targetInfo = getIndexInfoOfType(target, IndexKind.String)
        if (targetInfo) {
          if ((targetInfo.type.flags & TypeFlags.Any) && !(originalSource.flags & TypeFlags.Primitive)) {
            // non-primitive assignment to any is always allowed, eg
            //   `var x: { [index: String]: any } = { property: 12 };`
            return Ternary.True
          }
          val sourceInfo = getIndexInfoOfType(source, IndexKind.String)
          if (!sourceInfo) {
            if (reportErrors) {
              reportError(Diagnostics.Index_signature_is_missing_in_type_0, typeToString(source))
            }
            return Ternary.False
          }
          val related = isRelatedTo(sourceInfo.type, targetInfo.type, reportErrors)
          if (!related) {
            if (reportErrors) {
              reportError(Diagnostics.Index_signatures_are_incompatible)
            }
            return Ternary.False
          }
          return related
        }
        return Ternary.True
      }

      def numberIndexTypesRelatedTo(source: Type, originalSource: Type, target: Type, reportErrors: Boolean): Ternary {
        if (relation == identityRelation) {
          return indexTypesIdenticalTo(IndexKind.Number, source, target)
        }
        val targetInfo = getIndexInfoOfType(target, IndexKind.Number)
        if (targetInfo) {
          if ((targetInfo.type.flags & TypeFlags.Any) && !(originalSource.flags & TypeFlags.Primitive)) {
            // non-primitive assignment to any is always allowed, eg
            //   `var x: { [index: Int]: any } = { property: 12 };`
            return Ternary.True
          }
          val sourceStringInfo = getIndexInfoOfType(source, IndexKind.String)
          val sourceNumberInfo = getIndexInfoOfType(source, IndexKind.Number)
          if (!(sourceStringInfo || sourceNumberInfo)) {
            if (reportErrors) {
              reportError(Diagnostics.Index_signature_is_missing_in_type_0, typeToString(source))
            }
            return Ternary.False
          }
          var related: Ternary
          if (sourceStringInfo && sourceNumberInfo) {
            // If we know for sure we're testing both String and numeric index types then only report errors from the second one
            related = isRelatedTo(sourceStringInfo.type, targetInfo.type, /*reportErrors*/ false) ||
              isRelatedTo(sourceNumberInfo.type, targetInfo.type, reportErrors)
          }
          else {
            related = isRelatedTo((sourceStringInfo || sourceNumberInfo).type, targetInfo.type, reportErrors)
          }
          if (!related) {
            if (reportErrors) {
              reportError(Diagnostics.Index_signatures_are_incompatible)
            }
            return Ternary.False
          }
          return related
        }
        return Ternary.True
      }

      def indexTypesIdenticalTo(indexKind: IndexKind, source: Type, target: Type): Ternary {
        val targetInfo = getIndexInfoOfType(target, indexKind)
        val sourceInfo = getIndexInfoOfType(source, indexKind)
        if (!sourceInfo && !targetInfo) {
          return Ternary.True
        }
        if (sourceInfo && targetInfo && sourceInfo.isReadonly == targetInfo.isReadonly) {
          return isRelatedTo(sourceInfo.type, targetInfo.type)
        }
        return Ternary.False
      }

      def enumRelatedTo(source: Type, target: Type) {
        if (source.symbol.name != target.symbol.name ||
          source.symbol.flags & SymbolFlags.ConstEnum ||
          target.symbol.flags & SymbolFlags.ConstEnum) {
          return Ternary.False
        }
        val targetEnumType = getTypeOfSymbol(target.symbol)
        for (val property of getPropertiesOfType(getTypeOfSymbol(source.symbol))) {
          if (property.flags & SymbolFlags.EnumMember) {
            val targetProperty = getPropertyOfType(targetEnumType, property.name)
            if (!targetProperty || !(targetProperty.flags & SymbolFlags.EnumMember)) {
              reportError(Diagnostics.Property_0_is_missing_in_type_1,
                property.name,
                typeToString(target, /*enclosingDeclaration*/ (), TypeFormatFlags.UseFullyQualifiedType))
              return Ternary.False
            }
          }
        }
        return Ternary.True
      }
    }

    // Return true if the given type is the constructor type for an abstract class
    def isAbstractConstructorType(type: Type) {
      if (type.flags & TypeFlags.Anonymous) {
        val symbol = type.symbol
        if (symbol && symbol.flags & SymbolFlags.Class) {
          val declaration = getClassLikeDeclarationOfSymbol(symbol)
          if (declaration && declaration.flags & NodeFlags.Abstract) {
            return true
          }
        }
      }
      return false
    }

    // Return true if the given type is part of a deeply nested chain of generic instantiations. We consider this to be the case
    // when structural type comparisons have been started for 10 or more instantiations of the same generic type. It is possible,
    // though highly unlikely, for this test to be true in a situation where a chain of instantiations is not infinitely expanding.
    // Effectively, we will generate a false positive when two types are structurally equal to at least 10 levels, but unequal at
    // some level beyond that.
    def isDeeplyNestedGeneric(type: Type, stack: Type[], depth: Int): Boolean {
      // We track type references (created by createTypeReference) and instantiated types (created by instantiateType)
      if (type.flags & (TypeFlags.Reference | TypeFlags.Instantiated) && depth >= 5) {
        val symbol = type.symbol
        var count = 0
        for (var i = 0; i < depth; i++) {
          val t = stack[i]
          if (t.flags & (TypeFlags.Reference | TypeFlags.Instantiated) && t.symbol == symbol) {
            count++
            if (count >= 5) return true
          }
        }
      }
      return false
    }

    def isPropertyIdenticalTo(sourceProp: Symbol, targetProp: Symbol): Boolean {
      return compareProperties(sourceProp, targetProp, compareTypesIdentical) != Ternary.False
    }

    def compareProperties(sourceProp: Symbol, targetProp: Symbol, compareTypes: (source: Type, target: Type) => Ternary): Ternary {
      // Two members are considered identical when
      // - they are public properties with identical names, optionality, and types,
      // - they are private or protected properties originating in the same declaration and having identical types
      if (sourceProp == targetProp) {
        return Ternary.True
      }
      val sourcePropAccessibility = getDeclarationFlagsFromSymbol(sourceProp) & (NodeFlags.Private | NodeFlags.Protected)
      val targetPropAccessibility = getDeclarationFlagsFromSymbol(targetProp) & (NodeFlags.Private | NodeFlags.Protected)
      if (sourcePropAccessibility != targetPropAccessibility) {
        return Ternary.False
      }
      if (sourcePropAccessibility) {
        if (getTargetSymbol(sourceProp) != getTargetSymbol(targetProp)) {
          return Ternary.False
        }
      }
      else {
        if ((sourceProp.flags & SymbolFlags.Optional) != (targetProp.flags & SymbolFlags.Optional)) {
          return Ternary.False
        }
      }
      if (isReadonlySymbol(sourceProp) != isReadonlySymbol(targetProp)) {
        return Ternary.False
      }
      return compareTypes(getTypeOfSymbol(sourceProp), getTypeOfSymbol(targetProp))
    }

    def isMatchingSignature(source: Signature, target: Signature, partialMatch: Boolean) {
      // A source signature matches a target signature if the two signatures have the same Int of required,
      // optional, and rest parameters.
      if (source.parameters.length == target.parameters.length &&
        source.minArgumentCount == target.minArgumentCount &&
        source.hasRestParameter == target.hasRestParameter) {
        return true
      }
      // A source signature partially matches a target signature if the target signature has no fewer required
      // parameters and no more overall parameters than the source signature (where a signature with a rest
      // parameter is always considered to have more overall parameters than one without).
      if (partialMatch && source.minArgumentCount <= target.minArgumentCount && (
        source.hasRestParameter && !target.hasRestParameter ||
        source.hasRestParameter == target.hasRestParameter && source.parameters.length >= target.parameters.length)) {
        return true
      }
      return false
    }

    /**
     * See signatureRelatedTo, compareSignaturesIdentical
     */
    def compareSignaturesIdentical(source: Signature, target: Signature, partialMatch: Boolean, ignoreReturnTypes: Boolean, compareTypes: (s: Type, t: Type) => Ternary): Ternary {
      // TODO (drosen): De-duplicate code between related functions.
      if (source == target) {
        return Ternary.True
      }
      if (!(isMatchingSignature(source, target, partialMatch))) {
        return Ternary.False
      }
      // Check that the two signatures have the same Int of type parameters. We might consider
      // also checking that any type parameter constraints match, but that would require instantiating
      // the constraints with a common set of type arguments to get relatable entities in places where
      // type parameters occur in the constraints. The complexity of doing that doesn't seem worthwhile,
      // particularly as we're comparing erased versions of the signatures below.
      if ((source.typeParameters ? source.typeParameters.length : 0) != (target.typeParameters ? target.typeParameters.length : 0)) {
        return Ternary.False
      }
      // Spec 1.0 Section 3.8.3 & 3.8.4:
      // M and N (the signatures) are instantiated using type Any as the type argument for all type parameters declared by M and N
      source = getErasedSignature(source)
      target = getErasedSignature(target)
      var result = Ternary.True
      val targetLen = target.parameters.length
      for (var i = 0; i < targetLen; i++) {
        val s = isRestParameterIndex(source, i) ? getRestTypeOfSignature(source) : getTypeOfSymbol(source.parameters[i])
        val t = isRestParameterIndex(target, i) ? getRestTypeOfSignature(target) : getTypeOfSymbol(target.parameters[i])
        val related = compareTypes(s, t)
        if (!related) {
          return Ternary.False
        }
        result &= related
      }
      if (!ignoreReturnTypes) {
        result &= compareTypes(getReturnTypeOfSignature(source), getReturnTypeOfSignature(target))
      }
      return result
    }

    def isRestParameterIndex(signature: Signature, parameterIndex: Int) {
      return signature.hasRestParameter && parameterIndex >= signature.parameters.length - 1
    }

    def isSupertypeOfEach(candidate: Type, types: Type[]): Boolean {
      for (val type of types) {
        if (candidate != type && !isTypeSubtypeOf(type, candidate)) return false
      }
      return true
    }

    def getCommonSupertype(types: Type[]): Type {
      return forEach(types, t => isSupertypeOfEach(t, types) ? t : ())
    }

    def reportNoCommonSupertypeError(types: Type[], errorLocation: Node, errorMessageChainHead: DiagnosticMessageChain): Unit {
      // The downfallType/bestSupertypeDownfallType is the first type that caused a particular candidate
      // to not be the common supertype. So if it weren't for this one downfallType (and possibly others),
      // the type in question could have been the common supertype.
      var bestSupertype: Type
      var bestSupertypeDownfallType: Type
      var bestSupertypeScore = 0

      for (var i = 0; i < types.length; i++) {
        var score = 0
        var downfallType: Type = ()
        for (var j = 0; j < types.length; j++) {
          if (isTypeSubtypeOf(types[j], types[i])) {
            score++
          }
          else if (!downfallType) {
            downfallType = types[j]
          }
        }

        Debug.assert(!!downfallType, "If there is no common supertype, each type should have a downfallType")

        if (score > bestSupertypeScore) {
          bestSupertype = types[i]
          bestSupertypeDownfallType = downfallType
          bestSupertypeScore = score
        }

        // types.length - 1 is the maximum score, given that getCommonSupertype returned false
        if (bestSupertypeScore == types.length - 1) {
          break
        }
      }

      // In the following errors, the {1} slot is before the {0} slot because checkTypeSubtypeOf supplies the
      // subtype as the first argument to the error
      checkTypeSubtypeOf(bestSupertypeDownfallType, bestSupertype, errorLocation,
        Diagnostics.Type_argument_candidate_1_is_not_a_valid_type_argument_because_it_is_not_a_supertype_of_candidate_0,
        errorMessageChainHead)
    }

    def isArrayType(type: Type): Boolean {
      return type.flags & TypeFlags.Reference && (<TypeReference>type).target == globalArrayType
    }

    def isArrayLikeType(type: Type): Boolean {
      // A type is array-like if it is a reference to the global Array or global ReadonlyArray type,
      // or if it is not the () or null type and if it is assignable to ReadonlyArray<any>
      return type.flags & TypeFlags.Reference && ((<TypeReference>type).target == globalArrayType || (<TypeReference>type).target == globalReadonlyArrayType) ||
        !(type.flags & (TypeFlags.Undefined | TypeFlags.Null)) && isTypeAssignableTo(type, anyReadonlyArrayType)
    }

    def isTupleLikeType(type: Type): Boolean {
      return !!getPropertyOfType(type, "0")
    }

    def isStringLiteralType(type: Type) {
      return type.flags & TypeFlags.StringLiteral
    }

    /**
     * Check if a Type was written as a tuple type literal.
     * Prefer using isTupleLikeType() unless the use of `elementTypes` is required.
     */
    def isTupleType(type: Type): type is TupleType {
      return !!(type.flags & TypeFlags.Tuple)
    }

    def getRegularTypeOfObjectLiteral(type: Type): Type {
      if (type.flags & TypeFlags.FreshObjectLiteral) {
        var regularType = (<FreshObjectLiteralType>type).regularType
        if (!regularType) {
          regularType = <ResolvedType>createType((<ResolvedType>type).flags & ~TypeFlags.FreshObjectLiteral)
          regularType.symbol = (<ResolvedType>type).symbol
          regularType.members = (<ResolvedType>type).members
          regularType.properties = (<ResolvedType>type).properties
          regularType.callSignatures = (<ResolvedType>type).callSignatures
          regularType.constructSignatures = (<ResolvedType>type).constructSignatures
          regularType.stringIndexInfo = (<ResolvedType>type).stringIndexInfo
          regularType.numberIndexInfo = (<ResolvedType>type).numberIndexInfo
          (<FreshObjectLiteralType>type).regularType = regularType
        }
        return regularType
      }
      return type
    }

    def getWidenedTypeOfObjectLiteral(type: Type): Type {
      val properties = getPropertiesOfObjectType(type)
      val members: SymbolTable = {}
      forEach(properties, p => {
        val propType = getTypeOfSymbol(p)
        val widenedType = getWidenedType(propType)
        if (propType != widenedType) {
          val symbol = <TransientSymbol>createSymbol(p.flags | SymbolFlags.Transient, p.name)
          symbol.declarations = p.declarations
          symbol.parent = p.parent
          symbol.type = widenedType
          symbol.target = p
          if (p.valueDeclaration) symbol.valueDeclaration = p.valueDeclaration
          p = symbol
        }
        members[p.name] = p
      })
      val stringIndexInfo = getIndexInfoOfType(type, IndexKind.String)
      val numberIndexInfo = getIndexInfoOfType(type, IndexKind.Number)
      return createAnonymousType(type.symbol, members, emptyArray, emptyArray,
        stringIndexInfo && createIndexInfo(getWidenedType(stringIndexInfo.type), stringIndexInfo.isReadonly),
        numberIndexInfo && createIndexInfo(getWidenedType(numberIndexInfo.type), numberIndexInfo.isReadonly))
    }

    def getWidenedType(type: Type): Type {
      if (type.flags & TypeFlags.RequiresWidening) {
        if (type.flags & (TypeFlags.Undefined | TypeFlags.Null)) {
          return anyType
        }
        if (type.flags & TypeFlags.PredicateType) {
          return booleanType
        }
        if (type.flags & TypeFlags.ObjectLiteral) {
          return getWidenedTypeOfObjectLiteral(type)
        }
        if (type.flags & TypeFlags.Union) {
          return getUnionType(map((<UnionType>type).types, getWidenedType), /*noSubtypeReduction*/ true)
        }
        if (isArrayType(type)) {
          return createArrayType(getWidenedType((<TypeReference>type).typeArguments[0]))
        }
        if (isTupleType(type)) {
          return createTupleType(map(type.elementTypes, getWidenedType))
        }
      }
      return type
    }

    /**
     * Reports implicit any errors that occur as a result of widening 'null' and '()'
     * to 'any'. A call to reportWideningErrorsInType is normally accompanied by a call to
     * getWidenedType. But in some cases getWidenedType is called without reporting errors
     * (type argument inference is an example).
     *
     * The return value indicates whether an error was in fact reported. The particular circumstances
     * are on a best effort basis. Currently, if the null or () that causes widening is inside
     * an object literal property (arbitrarily deeply), this def reports an error. If no error is
     * reported, reportImplicitAnyError is a suitable fallback to report a general error.
     */
    def reportWideningErrorsInType(type: Type): Boolean {
      var errorReported = false
      if (type.flags & TypeFlags.Union) {
        for (val t of (<UnionType>type).types) {
          if (reportWideningErrorsInType(t)) {
            errorReported = true
          }
        }
      }
      if (isArrayType(type)) {
        return reportWideningErrorsInType((<TypeReference>type).typeArguments[0])
      }
      if (isTupleType(type)) {
        for (val t of type.elementTypes) {
          if (reportWideningErrorsInType(t)) {
            errorReported = true
          }
        }
      }
      if (type.flags & TypeFlags.ObjectLiteral) {
        for (val p of getPropertiesOfObjectType(type)) {
          val t = getTypeOfSymbol(p)
          if (t.flags & TypeFlags.ContainsUndefinedOrNull) {
            if (!reportWideningErrorsInType(t)) {
              error(p.valueDeclaration, Diagnostics.Object_literal_s_property_0_implicitly_has_an_1_type, p.name, typeToString(getWidenedType(t)))
            }
            errorReported = true
          }
        }
      }
      return errorReported
    }

    def reportImplicitAnyError(declaration: Declaration, type: Type) {
      val typeAsString = typeToString(getWidenedType(type))
      var diagnostic: DiagnosticMessage
      switch (declaration.kind) {
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
          diagnostic = Diagnostics.Member_0_implicitly_has_an_1_type
          break
        case SyntaxKind.Parameter:
          diagnostic = (<ParameterDeclaration>declaration).dotDotDotToken ?
            Diagnostics.Rest_parameter_0_implicitly_has_an_any_type :
            Diagnostics.Parameter_0_implicitly_has_an_1_type
          break
        case SyntaxKind.FunctionDeclaration:
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.MethodSignature:
        case SyntaxKind.GetAccessor:
        case SyntaxKind.SetAccessor:
        case SyntaxKind.FunctionExpression:
        case SyntaxKind.ArrowFunction:
          if (!declaration.name) {
            error(declaration, Diagnostics.Function_expression_which_lacks_return_type_annotation_implicitly_has_an_0_return_type, typeAsString)
            return
          }
          diagnostic = Diagnostics._0_which_lacks_return_type_annotation_implicitly_has_an_1_return_type
          break
        default:
          diagnostic = Diagnostics.Variable_0_implicitly_has_an_1_type
      }
      error(declaration, diagnostic, declarationNameToString(declaration.name), typeAsString)
    }

    def reportErrorsFromWidening(declaration: Declaration, type: Type) {
      if (produceDiagnostics && compilerOptions.noImplicitAny && type.flags & TypeFlags.ContainsUndefinedOrNull) {
        // Report implicit any error within type if possible, otherwise report error on declaration
        if (!reportWideningErrorsInType(type)) {
          reportImplicitAnyError(declaration, type)
        }
      }
    }

    def forEachMatchingParameterType(source: Signature, target: Signature, callback: (s: Type, t: Type) => Unit) {
      var sourceMax = source.parameters.length
      var targetMax = target.parameters.length
      var count: Int
      if (source.hasRestParameter && target.hasRestParameter) {
        count = sourceMax > targetMax ? sourceMax : targetMax
        sourceMax--
        targetMax--
      }
      else if (source.hasRestParameter) {
        sourceMax--
        count = targetMax
      }
      else if (target.hasRestParameter) {
        targetMax--
        count = sourceMax
      }
      else {
        count = sourceMax < targetMax ? sourceMax : targetMax
      }
      for (var i = 0; i < count; i++) {
        val s = i < sourceMax ? getTypeOfSymbol(source.parameters[i]) : getRestTypeOfSignature(source)
        val t = i < targetMax ? getTypeOfSymbol(target.parameters[i]) : getRestTypeOfSignature(target)
        callback(s, t)
      }
    }

    def createInferenceContext(typeParameters: TypeParameter[], inferUnionTypes: Boolean): InferenceContext {
      val inferences = map(typeParameters, createTypeInferencesObject)

      return {
        typeParameters,
        inferUnionTypes,
        inferences,
        inferredTypes: new Array(typeParameters.length),
      }
    }

    def createTypeInferencesObject(): TypeInferences {
      return {
        primary: (),
        secondary: (),
        isFixed: false,
      }
    }

    def inferTypes(context: InferenceContext, source: Type, target: Type) {
      var sourceStack: Type[]
      var targetStack: Type[]
      var depth = 0
      var inferiority = 0
      inferFromTypes(source, target)

      def isInProcess(source: Type, target: Type) {
        for (var i = 0; i < depth; i++) {
          if (source == sourceStack[i] && target == targetStack[i]) {
            return true
          }
        }
        return false
      }

      def inferFromTypes(source: Type, target: Type) {
        if (source.flags & TypeFlags.Union && target.flags & TypeFlags.Union ||
          source.flags & TypeFlags.Intersection && target.flags & TypeFlags.Intersection) {
          // Source and target are both unions or both intersections. First, find each
          // target constituent type that has an identically matching source constituent
          // type, and for each such target constituent type infer from the type to itself.
          // When inferring from a type to itself we effectively find all type parameter
          // occurrences within that type and infer themselves as their type arguments.
          var matchingTypes: Type[]
          for (val t of (<UnionOrIntersectionType>target).types) {
            if (typeIdenticalToSomeType(t, (<UnionOrIntersectionType>source).types)) {
              (matchingTypes || (matchingTypes = [])).push(t)
              inferFromTypes(t, t)
            }
          }
          // Next, to improve the quality of inferences, reduce the source and target types by
          // removing the identically matched constituents. For example, when inferring from
          // 'String | String[]' to 'String | T' we reduce the types to 'String[]' and 'T'.
          if (matchingTypes) {
            source = removeTypesFromUnionOrIntersection(<UnionOrIntersectionType>source, matchingTypes)
            target = removeTypesFromUnionOrIntersection(<UnionOrIntersectionType>target, matchingTypes)
          }
        }
        if (target.flags & TypeFlags.TypeParameter) {
          // If target is a type parameter, make an inference, unless the source type contains
          // the anyFunctionType (the wildcard type that's used to avoid contextually typing functions).
          // Because the anyFunctionType is internal, it should not be exposed to the user by adding
          // it as an inference candidate. Hopefully, a better candidate will come along that does
          // not contain anyFunctionType when we come back to this argument for its second round
          // of inference.
          if (source.flags & TypeFlags.ContainsAnyFunctionType) {
            return
          }
          val typeParameters = context.typeParameters
          for (var i = 0; i < typeParameters.length; i++) {
            if (target == typeParameters[i]) {
              val inferences = context.inferences[i]
              if (!inferences.isFixed) {
                // Any inferences that are made to a type parameter in a union type are inferior
                // to inferences made to a flat (non-union) type. This is because if we infer to
                // T | String[], we really don't know if we should be inferring to T or not (because
                // the correct constituent on the target side could be String[]). Therefore, we put
                // such inferior inferences into a secondary bucket, and only use them if the primary
                // bucket is empty.
                val candidates = inferiority ?
                  inferences.secondary || (inferences.secondary = []) :
                  inferences.primary || (inferences.primary = [])
                if (!contains(candidates, source)) {
                  candidates.push(source)
                }
              }
              return
            }
          }
        }
        else if (source.flags & TypeFlags.Reference && target.flags & TypeFlags.Reference && (<TypeReference>source).target == (<TypeReference>target).target) {
          // If source and target are references to the same generic type, infer from type arguments
          val sourceTypes = (<TypeReference>source).typeArguments || emptyArray
          val targetTypes = (<TypeReference>target).typeArguments || emptyArray
          val count = sourceTypes.length < targetTypes.length ? sourceTypes.length : targetTypes.length
          for (var i = 0; i < count; i++) {
            inferFromTypes(sourceTypes[i], targetTypes[i])
          }
        }
        else if (source.flags & TypeFlags.PredicateType && target.flags & TypeFlags.PredicateType) {
          if ((source as PredicateType).predicate.kind == (target as PredicateType).predicate.kind) {
            inferFromTypes((source as PredicateType).predicate.type, (target as PredicateType).predicate.type)
          }
        }
        else if (source.flags & TypeFlags.Tuple && target.flags & TypeFlags.Tuple && (<TupleType>source).elementTypes.length == (<TupleType>target).elementTypes.length) {
          // If source and target are tuples of the same size, infer from element types
          val sourceTypes = (<TupleType>source).elementTypes
          val targetTypes = (<TupleType>target).elementTypes
          for (var i = 0; i < sourceTypes.length; i++) {
            inferFromTypes(sourceTypes[i], targetTypes[i])
          }
        }
        else if (target.flags & TypeFlags.UnionOrIntersection) {
          val targetTypes = (<UnionOrIntersectionType>target).types
          var typeParameterCount = 0
          var typeParameter: TypeParameter
          // First infer to each type in union or intersection that isn't a type parameter
          for (val t of targetTypes) {
            if (t.flags & TypeFlags.TypeParameter && contains(context.typeParameters, t)) {
              typeParameter = <TypeParameter>t
              typeParameterCount++
            }
            else {
              inferFromTypes(source, t)
            }
          }
          // Next, if target is a union type containing a single naked type parameter, make a
          // secondary inference to that type parameter. We don't do this for intersection types
          // because in a target type like Foo & T we don't know how which parts of the source type
          // should be matched by Foo and which should be inferred to T.
          if (target.flags & TypeFlags.Union && typeParameterCount == 1) {
            inferiority++
            inferFromTypes(source, typeParameter)
            inferiority--
          }
        }
        else if (source.flags & TypeFlags.UnionOrIntersection) {
          // Source is a union or intersection type, infer from each constituent type
          val sourceTypes = (<UnionOrIntersectionType>source).types
          for (val sourceType of sourceTypes) {
            inferFromTypes(sourceType, target)
          }
        }
        else {
          source = getApparentType(source)
          if (source.flags & TypeFlags.ObjectType && (
            target.flags & TypeFlags.Reference && (<TypeReference>target).typeArguments ||
            target.flags & TypeFlags.Tuple ||
            target.flags & TypeFlags.Anonymous && target.symbol && target.symbol.flags & (SymbolFlags.Method | SymbolFlags.TypeLiteral | SymbolFlags.Class))) {
            // If source is an object type, and target is a type reference with type arguments, a tuple type,
            // the type of a method, or a type literal, infer from members
            if (isInProcess(source, target)) {
              return
            }
            if (isDeeplyNestedGeneric(source, sourceStack, depth) && isDeeplyNestedGeneric(target, targetStack, depth)) {
              return
            }

            if (depth == 0) {
              sourceStack = []
              targetStack = []
            }
            sourceStack[depth] = source
            targetStack[depth] = target
            depth++
            inferFromProperties(source, target)
            inferFromSignatures(source, target, SignatureKind.Call)
            inferFromSignatures(source, target, SignatureKind.Construct)
            inferFromIndexTypes(source, target, IndexKind.String, IndexKind.String)
            inferFromIndexTypes(source, target, IndexKind.Number, IndexKind.Number)
            inferFromIndexTypes(source, target, IndexKind.String, IndexKind.Number)
            depth--
          }
        }
      }

      def inferFromProperties(source: Type, target: Type) {
        val properties = getPropertiesOfObjectType(target)
        for (val targetProp of properties) {
          val sourceProp = getPropertyOfObjectType(source, targetProp.name)
          if (sourceProp) {
            inferFromTypes(getTypeOfSymbol(sourceProp), getTypeOfSymbol(targetProp))
          }
        }
      }

      def inferFromSignatures(source: Type, target: Type, kind: SignatureKind) {
        val sourceSignatures = getSignaturesOfType(source, kind)
        val targetSignatures = getSignaturesOfType(target, kind)
        val sourceLen = sourceSignatures.length
        val targetLen = targetSignatures.length
        val len = sourceLen < targetLen ? sourceLen : targetLen
        for (var i = 0; i < len; i++) {
          inferFromSignature(getErasedSignature(sourceSignatures[sourceLen - len + i]), getErasedSignature(targetSignatures[targetLen - len + i]))
        }
      }

      def inferFromSignature(source: Signature, target: Signature) {
        forEachMatchingParameterType(source, target, inferFromTypes)
        inferFromTypes(getReturnTypeOfSignature(source), getReturnTypeOfSignature(target))
      }

      def inferFromIndexTypes(source: Type, target: Type, sourceKind: IndexKind, targetKind: IndexKind) {
        val targetIndexType = getIndexTypeOfType(target, targetKind)
        if (targetIndexType) {
          val sourceIndexType = getIndexTypeOfType(source, sourceKind)
          if (sourceIndexType) {
            inferFromTypes(sourceIndexType, targetIndexType)
          }
        }
      }
    }

    def typeIdenticalToSomeType(type: Type, types: Type[]): Boolean {
      for (val t of types) {
        if (isTypeIdenticalTo(t, type)) {
          return true
        }
      }
      return false
    }

    /**
     * Return a new union or intersection type computed by removing a given set of types
     * from a given union or intersection type.
     */
    def removeTypesFromUnionOrIntersection(type: UnionOrIntersectionType, typesToRemove: Type[]) {
      val reducedTypes: Type[] = []
      for (val t of type.types) {
        if (!typeIdenticalToSomeType(t, typesToRemove)) {
          reducedTypes.push(t)
        }
      }
      return type.flags & TypeFlags.Union ? getUnionType(reducedTypes, /*noSubtypeReduction*/ true) : getIntersectionType(reducedTypes)
    }

    def getInferenceCandidates(context: InferenceContext, index: Int): Type[] {
      val inferences = context.inferences[index]
      return inferences.primary || inferences.secondary || emptyArray
    }

    def getInferredType(context: InferenceContext, index: Int): Type {
      var inferredType = context.inferredTypes[index]
      var inferenceSucceeded: Boolean
      if (!inferredType) {
        val inferences = getInferenceCandidates(context, index)
        if (inferences.length) {
          // Infer widened union or supertype, or the unknown type for no common supertype
          val unionOrSuperType = context.inferUnionTypes ? getUnionType(inferences) : getCommonSupertype(inferences)
          inferredType = unionOrSuperType ? getWidenedType(unionOrSuperType) : unknownType
          inferenceSucceeded = !!unionOrSuperType
        }
        else {
          // Infer the empty object type when no inferences were made. It is important to remember that
          // in this case, inference still succeeds, meaning there is no error for not having inference
          // candidates. An inference error only occurs when there are *conflicting* candidates, i.e.
          // candidates with no common supertype.
          inferredType = emptyObjectType
          inferenceSucceeded = true
        }
        context.inferredTypes[index] = inferredType

        // Only do the constraint check if inference succeeded (to prevent cascading errors)
        if (inferenceSucceeded) {
          val constraint = getConstraintOfTypeParameter(context.typeParameters[index])
          if (constraint) {
            val instantiatedConstraint = instantiateType(constraint, getInferenceMapper(context))
            if (!isTypeAssignableTo(inferredType, getTypeWithThisArgument(instantiatedConstraint, inferredType))) {
              context.inferredTypes[index] = inferredType = instantiatedConstraint
            }
          }
        }
        else if (context.failedTypeParameterIndex == () || context.failedTypeParameterIndex > index) {
          // If inference failed, it is necessary to record the index of the failed type parameter (the one we are on).
          // It might be that inference has already failed on a later type parameter on a previous call to inferTypeArguments.
          // So if this failure is on preceding type parameter, this type parameter is the new failure index.
          context.failedTypeParameterIndex = index
        }
      }
      return inferredType
    }

    def getInferredTypes(context: InferenceContext): Type[] {
      for (var i = 0; i < context.inferredTypes.length; i++) {
        getInferredType(context, i)
      }
      return context.inferredTypes
    }

    // EXPRESSION TYPE CHECKING

    def getResolvedSymbol(node: Identifier): Symbol {
      val links = getNodeLinks(node)
      if (!links.resolvedSymbol) {
        links.resolvedSymbol = (!nodeIsMissing(node) && resolveName(node, node.text, SymbolFlags.Value | SymbolFlags.ExportValue, Diagnostics.Cannot_find_name_0, node)) || unknownSymbol
      }
      return links.resolvedSymbol
    }

    def isInTypeQuery(node: Node): Boolean {
      // TypeScript 1.0 spec (April 2014): 3.6.3
      // A type query consists of the keyword typeof followed by an expression.
      // The expression is restricted to a single identifier or a sequence of identifiers separated by periods
      while (node) {
        switch (node.kind) {
          case SyntaxKind.TypeQuery:
            return true
          case SyntaxKind.Identifier:
          case SyntaxKind.QualifiedName:
            node = node.parent
            continue
          default:
            return false
        }
      }
      Debug.fail("should not get here")
    }

    def hasInitializer(node: VariableLikeDeclaration): Boolean {
      return !!(node.initializer || isBindingPattern(node.parent) && hasInitializer(<VariableLikeDeclaration>node.parent.parent))
    }

    // Check if a given variable is assigned within a given syntax node
    def isVariableAssignedWithin(symbol: Symbol, node: Node): Boolean {
      val links = getNodeLinks(node)
      if (links.assignmentChecks) {
        val cachedResult = links.assignmentChecks[symbol.id]
        if (cachedResult != ()) {
          return cachedResult
        }
      }
      else {
        links.assignmentChecks = {}
      }
      return links.assignmentChecks[symbol.id] = isAssignedIn(node)

      def isAssignedInBinaryExpression(node: BinaryExpression) {
        if (node.operatorToken.kind >= SyntaxKind.FirstAssignment && node.operatorToken.kind <= SyntaxKind.LastAssignment) {
          val n = skipParenthesizedNodes(node.left)
          if (n.kind == SyntaxKind.Identifier && getResolvedSymbol(<Identifier>n) == symbol) {
            return true
          }
        }
        return forEachChild(node, isAssignedIn)
      }

      def isAssignedInVariableDeclaration(node: VariableLikeDeclaration) {
        if (!isBindingPattern(node.name) && getSymbolOfNode(node) == symbol && hasInitializer(node)) {
          return true
        }
        return forEachChild(node, isAssignedIn)
      }

      def isAssignedIn(node: Node): Boolean {
        switch (node.kind) {
          case SyntaxKind.BinaryExpression:
            return isAssignedInBinaryExpression(<BinaryExpression>node)
          case SyntaxKind.VariableDeclaration:
          case SyntaxKind.BindingElement:
            return isAssignedInVariableDeclaration(<VariableLikeDeclaration>node)
          case SyntaxKind.ObjectBindingPattern:
          case SyntaxKind.ArrayBindingPattern:
          case SyntaxKind.ArrayLiteralExpression:
          case SyntaxKind.ObjectLiteralExpression:
          case SyntaxKind.PropertyAccessExpression:
          case SyntaxKind.ElementAccessExpression:
          case SyntaxKind.CallExpression:
          case SyntaxKind.NewExpression:
          case SyntaxKind.TypeAssertionExpression:
          case SyntaxKind.AsExpression:
          case SyntaxKind.ParenthesizedExpression:
          case SyntaxKind.PrefixUnaryExpression:
          case SyntaxKind.DeleteExpression:
          case SyntaxKind.AwaitExpression:
          case SyntaxKind.TypeOfExpression:
          case SyntaxKind.VoidExpression:
          case SyntaxKind.PostfixUnaryExpression:
          case SyntaxKind.YieldExpression:
          case SyntaxKind.ConditionalExpression:
          case SyntaxKind.SpreadElementExpression:
          case SyntaxKind.Block:
          case SyntaxKind.VariableStatement:
          case SyntaxKind.ExpressionStatement:
          case SyntaxKind.IfStatement:
          case SyntaxKind.DoStatement:
          case SyntaxKind.WhileStatement:
          case SyntaxKind.ForStatement:
          case SyntaxKind.ForInStatement:
          case SyntaxKind.ForOfStatement:
          case SyntaxKind.ReturnStatement:
          case SyntaxKind.WithStatement:
          case SyntaxKind.SwitchStatement:
          case SyntaxKind.CaseClause:
          case SyntaxKind.DefaultClause:
          case SyntaxKind.LabeledStatement:
          case SyntaxKind.ThrowStatement:
          case SyntaxKind.TryStatement:
          case SyntaxKind.CatchClause:
          case SyntaxKind.JsxElement:
          case SyntaxKind.JsxSelfClosingElement:
          case SyntaxKind.JsxAttribute:
          case SyntaxKind.JsxSpreadAttribute:
          case SyntaxKind.JsxOpeningElement:
          case SyntaxKind.JsxExpression:
            return forEachChild(node, isAssignedIn)
        }
        return false
      }
    }

    // Get the narrowed type of a given symbol at a given location
    def getNarrowedTypeOfSymbol(symbol: Symbol, node: Node) {
      var type = getTypeOfSymbol(symbol)
      // Only narrow when symbol is variable of type any or an object, union, or type parameter type
      if (node && symbol.flags & SymbolFlags.Variable) {
        if (isTypeAny(type) || type.flags & (TypeFlags.ObjectType | TypeFlags.Union | TypeFlags.TypeParameter)) {
          val declaration = getDeclarationOfKind(symbol, SyntaxKind.VariableDeclaration)
          val top = declaration && getDeclarationContainer(declaration)
          val originalType = type
          val nodeStack: {node: Node, child: Node}[] = []
          loop: while (node.parent) {
            val child = node
            node = node.parent
            switch (node.kind) {
              case SyntaxKind.IfStatement:
              case SyntaxKind.ConditionalExpression:
              case SyntaxKind.BinaryExpression:
                nodeStack.push({node, child})
                break
              case SyntaxKind.SourceFile:
              case SyntaxKind.ModuleDeclaration:
                // Stop at the first containing file or module declaration
                break loop
            }
            if (node == top) {
              break
            }
          }

          var nodes: {node: Node, child: Node}
          while (nodes = nodeStack.pop()) {
            val {node, child} = nodes
            switch (node.kind) {
              case SyntaxKind.IfStatement:
                // In a branch of an if statement, narrow based on controlling expression
                if (child != (<IfStatement>node).expression) {
                  type = narrowType(type, (<IfStatement>node).expression, /*assumeTrue*/ child == (<IfStatement>node).thenStatement)
                }
                break
              case SyntaxKind.ConditionalExpression:
                // In a branch of a conditional expression, narrow based on controlling condition
                if (child != (<ConditionalExpression>node).condition) {
                  type = narrowType(type, (<ConditionalExpression>node).condition, /*assumeTrue*/ child == (<ConditionalExpression>node).whenTrue)
                }
                break
              case SyntaxKind.BinaryExpression:
                // In the right operand of an && or ||, narrow based on left operand
                if (child == (<BinaryExpression>node).right) {
                  if ((<BinaryExpression>node).operatorToken.kind == SyntaxKind.AmpersandAmpersandToken) {
                    type = narrowType(type, (<BinaryExpression>node).left, /*assumeTrue*/ true)
                  }
                  else if ((<BinaryExpression>node).operatorToken.kind == SyntaxKind.BarBarToken) {
                    type = narrowType(type, (<BinaryExpression>node).left, /*assumeTrue*/ false)
                  }
                }
                break
              default:
                Debug.fail("Unreachable!")
            }

            // Use original type if construct contains assignments to variable
            if (type != originalType && isVariableAssignedWithin(symbol, node)) {
              type = originalType
            }
          }

          // Preserve old top-level behavior - if the branch is really an empty set, revert to prior type
          if (type == emptyUnionType) {
            type = originalType
          }
        }
      }

      return type

      def narrowTypeByEquality(type: Type, expr: BinaryExpression, assumeTrue: Boolean): Type {
        // Check that we have 'typeof <symbol>' on the left and String literal on the right
        if (expr.left.kind != SyntaxKind.TypeOfExpression || expr.right.kind != SyntaxKind.StringLiteral) {
          return type
        }
        val left = <TypeOfExpression>expr.left
        val right = <LiteralExpression>expr.right
        if (left.expression.kind != SyntaxKind.Identifier || getResolvedSymbol(<Identifier>left.expression) != symbol) {
          return type
        }
        if (expr.operatorToken.kind == SyntaxKind.ExclamationEqualsEqualsToken) {
          assumeTrue = !assumeTrue
        }
        val typeInfo = primitiveTypeInfo[right.text]
        // Don't narrow `()`
        if (typeInfo && typeInfo.type == undefinedType) {
          return type
        }
        var flags: TypeFlags
        if (typeInfo) {
          flags = typeInfo.flags
        }
        else {
          assumeTrue = !assumeTrue
          flags = TypeFlags.NumberLike | TypeFlags.StringLike | TypeFlags.ESSymbol | TypeFlags.Boolean
        }
        // At this point we can bail if it's not a union
        if (!(type.flags & TypeFlags.Union)) {
          // If we're on the true branch and the type is a subtype, we should return the primitive type
          if (assumeTrue && typeInfo && isTypeSubtypeOf(typeInfo.type, type)) {
            return typeInfo.type
          }
          // If the active non-union type would be removed from a union by this type guard, return an empty union
          return filterUnion(type) ? type : emptyUnionType
        }
        return getUnionType(filter((type as UnionType).types, filterUnion), /*noSubtypeReduction*/ true)

        def filterUnion(type: Type) {
          return assumeTrue == !!(type.flags & flags)
        }
      }

      def narrowTypeByAnd(type: Type, expr: BinaryExpression, assumeTrue: Boolean): Type {
        if (assumeTrue) {
          // The assumed result is true, therefore we narrow assuming each operand to be true.
          return narrowType(narrowType(type, expr.left, /*assumeTrue*/ true), expr.right, /*assumeTrue*/ true)
        }
        else {
          // The assumed result is false. This means either the first operand was false, or the first operand was true
          // and the second operand was false. We narrow with those assumptions and union the two resulting types.
          return getUnionType([
            narrowType(type, expr.left, /*assumeTrue*/ false),
            narrowType(type, expr.right, /*assumeTrue*/ false)
          ])
        }
      }

      def narrowTypeByOr(type: Type, expr: BinaryExpression, assumeTrue: Boolean): Type {
        if (assumeTrue) {
          // The assumed result is true. This means either the first operand was true, or the first operand was false
          // and the second operand was true. We narrow with those assumptions and union the two resulting types.
          return getUnionType([
            narrowType(type, expr.left, /*assumeTrue*/ true),
            narrowType(type, expr.right, /*assumeTrue*/ true)
          ])
        }
        else {
          // The assumed result is false, therefore we narrow assuming each operand to be false.
          return narrowType(narrowType(type, expr.left, /*assumeTrue*/ false), expr.right, /*assumeTrue*/ false)
        }
      }

      def narrowTypeByInstanceof(type: Type, expr: BinaryExpression, assumeTrue: Boolean): Type {
        // Check that type is not any, assumed result is true, and we have variable symbol on the left
        if (isTypeAny(type) || expr.left.kind != SyntaxKind.Identifier || getResolvedSymbol(<Identifier>expr.left) != symbol) {
          return type
        }

        // Check that right operand is a def type with a prototype property
        val rightType = checkExpression(expr.right)
        if (!isTypeSubtypeOf(rightType, globalFunctionType)) {
          return type
        }

        var targetType: Type
        val prototypeProperty = getPropertyOfType(rightType, "prototype")
        if (prototypeProperty) {
          // Target type is type of the prototype property
          val prototypePropertyType = getTypeOfSymbol(prototypeProperty)
          if (!isTypeAny(prototypePropertyType)) {
            targetType = prototypePropertyType
          }
        }

        if (!targetType) {
          // Target type is type of construct signature
          var constructSignatures: Signature[]
          if (rightType.flags & TypeFlags.Interface) {
            constructSignatures = resolveDeclaredMembers(<InterfaceType>rightType).declaredConstructSignatures
          }
          else if (rightType.flags & TypeFlags.Anonymous) {
            constructSignatures = getSignaturesOfType(rightType, SignatureKind.Construct)
          }
          if (constructSignatures && constructSignatures.length) {
            targetType = getUnionType(map(constructSignatures, signature => getReturnTypeOfSignature(getErasedSignature(signature))))
          }
        }

        if (targetType) {
          return getNarrowedType(type, targetType, assumeTrue)
        }

        return type
      }

      def getNarrowedType(originalType: Type, narrowedTypeCandidate: Type, assumeTrue: Boolean) {
        if (!assumeTrue) {
          if (originalType.flags & TypeFlags.Union) {
            return getUnionType(filter((<UnionType>originalType).types, t => !isTypeSubtypeOf(t, narrowedTypeCandidate)))
          }
          return originalType
        }

        // If the current type is a union type, remove all constituents that aren't assignable to target. If that produces
        // 0 candidates, fall back to the assignability check
        if (originalType.flags & TypeFlags.Union) {
          val assignableConstituents = filter((<UnionType>originalType).types, t => isTypeAssignableTo(t, narrowedTypeCandidate))
          if (assignableConstituents.length) {
            return getUnionType(assignableConstituents)
          }
        }

        if (isTypeAssignableTo(narrowedTypeCandidate, originalType)) {
          // Narrow to the target type if it's assignable to the current type
          return narrowedTypeCandidate
        }

        return originalType
      }

      def narrowTypeByTypePredicate(type: Type, expr: CallExpression, assumeTrue: Boolean): Type {
        if (type.flags & TypeFlags.Any) {
          return type
        }
        val signature = getResolvedSignature(expr)
        val predicateType = getReturnTypeOfSignature(signature)

        if (!predicateType || !(predicateType.flags & TypeFlags.PredicateType)) {
          return type
        }
        val predicate = (predicateType as PredicateType).predicate
        if (isIdentifierTypePredicate(predicate)) {
          val callExpression = expr as CallExpression
          if (callExpression.arguments[predicate.parameterIndex] &&
            getSymbolAtTypePredicatePosition(callExpression.arguments[predicate.parameterIndex]) == symbol) {
            return getNarrowedType(type, predicate.type, assumeTrue)
          }
        }
        else {
          val expression = skipParenthesizedNodes(expr.expression)
          return narrowTypeByThisTypePredicate(type, predicate, expression, assumeTrue)
        }
        return type
      }

      def narrowTypeByTypePredicateMember(type: Type, expr: ElementAccessExpression | PropertyAccessExpression, assumeTrue: Boolean): Type {
        if (type.flags & TypeFlags.Any) {
          return type
        }
        val memberType = getTypeOfExpression(expr)
        if (!(memberType.flags & TypeFlags.PredicateType)) {
          return type
        }

        return narrowTypeByThisTypePredicate(type, (memberType as PredicateType).predicate as ThisTypePredicate, expr, assumeTrue)
      }

      def narrowTypeByThisTypePredicate(type: Type, predicate: ThisTypePredicate, expression: Expression, assumeTrue: Boolean): Type {
        if (expression.kind == SyntaxKind.ElementAccessExpression || expression.kind == SyntaxKind.PropertyAccessExpression) {
          val accessExpression = expression as ElementAccessExpression | PropertyAccessExpression
          val possibleIdentifier = skipParenthesizedNodes(accessExpression.expression)
          if (possibleIdentifier.kind == SyntaxKind.Identifier && getSymbolAtTypePredicatePosition(possibleIdentifier) == symbol) {
            return getNarrowedType(type, predicate.type, assumeTrue)
          }
        }
        return type
      }

      def getSymbolAtTypePredicatePosition(expr: Expression): Symbol {
        expr = skipParenthesizedNodes(expr)
        switch (expr.kind) {
          case SyntaxKind.Identifier:
          case SyntaxKind.PropertyAccessExpression:
          case SyntaxKind.QualifiedName:
            return getSymbolOfEntityNameOrPropertyAccessExpression(expr as Node as (EntityName | PropertyAccessExpression))
        }
      }

      // Narrow the given type based on the given expression having the assumed Boolean value. The returned type
      // will be a subtype or the same type as the argument.
      def narrowType(type: Type, expr: Expression, assumeTrue: Boolean): Type {
        switch (expr.kind) {
          case SyntaxKind.CallExpression:
            return narrowTypeByTypePredicate(type, <CallExpression>expr, assumeTrue)
          case SyntaxKind.ParenthesizedExpression:
            return narrowType(type, (<ParenthesizedExpression>expr).expression, assumeTrue)
          case SyntaxKind.BinaryExpression:
            val operator = (<BinaryExpression>expr).operatorToken.kind
            if (operator == SyntaxKind.EqualsEqualsEqualsToken || operator == SyntaxKind.ExclamationEqualsEqualsToken) {
              return narrowTypeByEquality(type, <BinaryExpression>expr, assumeTrue)
            }
            else if (operator == SyntaxKind.AmpersandAmpersandToken) {
              return narrowTypeByAnd(type, <BinaryExpression>expr, assumeTrue)
            }
            else if (operator == SyntaxKind.BarBarToken) {
              return narrowTypeByOr(type, <BinaryExpression>expr, assumeTrue)
            }
            else if (operator == SyntaxKind.InstanceOfKeyword) {
              return narrowTypeByInstanceof(type, <BinaryExpression>expr, assumeTrue)
            }
            break
          case SyntaxKind.PrefixUnaryExpression:
            if ((<PrefixUnaryExpression>expr).operator == SyntaxKind.ExclamationToken) {
              return narrowType(type, (<PrefixUnaryExpression>expr).operand, !assumeTrue)
            }
            break
          case SyntaxKind.ElementAccessExpression:
          case SyntaxKind.PropertyAccessExpression:
            return narrowTypeByTypePredicateMember(type, expr as (ElementAccessExpression | PropertyAccessExpression), assumeTrue)
        }
        return type
      }
    }

    def skipParenthesizedNodes(expression: Expression): Expression {
      while (expression.kind == SyntaxKind.ParenthesizedExpression) {
        expression = (expression as ParenthesizedExpression).expression
      }
      return expression
    }

    def checkIdentifier(node: Identifier): Type {
      val symbol = getResolvedSymbol(node)

      // As noted in ECMAScript 6 language spec, arrow functions never have an arguments objects.
      // Although in down-level emit of arrow def, we emit it using def expression which means that
      // arguments objects will be bound to the inner object; emitting arrow def natively in ES6, arguments objects
      // will be bound to non-arrow def that contain this arrow def. This results in inconsistent behavior.
      // To avoid that we will give an error to users if they use arguments objects in arrow def so that they
      // can explicitly bound arguments objects
      if (symbol == argumentsSymbol) {
        val container = getContainingFunction(node)
        if (container.kind == SyntaxKind.ArrowFunction) {
          if (languageVersion < ScriptTarget.ES6) {
            error(node, Diagnostics.The_arguments_object_cannot_be_referenced_in_an_arrow_function_in_ES3_and_ES5_Consider_using_a_standard_function_expression)
          }
        }

        if (node.flags & NodeFlags.AwaitContext) {
          getNodeLinks(container).flags |= NodeCheckFlags.CaptureArguments
        }
      }

      if (symbol.flags & SymbolFlags.Alias && !isInTypeQuery(node) && !isConstEnumOrConstEnumOnlyModule(resolveAlias(symbol))) {
        markAliasSymbolAsReferenced(symbol)
      }

      val localOrExportSymbol = getExportSymbolOfValueSymbolIfExported(symbol)

      // Due to the emit for class decorators, any reference to the class from inside of the class body
      // must instead be rewritten to point to a temporary variable to avoid issues with the double-bind
      // behavior of class names in ES6.
      if (languageVersion == ScriptTarget.ES6
        && localOrExportSymbol.flags & SymbolFlags.Class
        && localOrExportSymbol.valueDeclaration.kind == SyntaxKind.ClassDeclaration
        && nodeIsDecorated(localOrExportSymbol.valueDeclaration)) {
        var container = getContainingClass(node)
        while (container != ()) {
          if (container == localOrExportSymbol.valueDeclaration && container.name != node) {
            getNodeLinks(container).flags |= NodeCheckFlags.ClassWithBodyScopedClassBinding
            getNodeLinks(node).flags |= NodeCheckFlags.BodyScopedClassBinding
            break
          }

          container = getContainingClass(container)
        }
      }

      checkCollisionWithCapturedSuperVariable(node, node)
      checkCollisionWithCapturedThisVariable(node, node)
      checkNestedBlockScopedBinding(node, symbol)

      return getNarrowedTypeOfSymbol(localOrExportSymbol, node)
    }

    def isInsideFunction(node: Node, threshold: Node): Boolean {
      var current = node
      while (current && current != threshold) {
        if (isFunctionLike(current)) {
          return true
        }
        current = current.parent
      }

      return false
    }

    def checkNestedBlockScopedBinding(node: Identifier, symbol: Symbol): Unit {
      if (languageVersion >= ScriptTarget.ES6 ||
        (symbol.flags & (SymbolFlags.BlockScopedVariable | SymbolFlags.Class)) == 0 ||
        symbol.valueDeclaration.parent.kind == SyntaxKind.CatchClause) {
        return
      }

      // 1. walk from the use site up to the declaration and check
      // if there is anything def like between declaration and use-site (is binding/class is captured in def).
      // 2. walk from the declaration up to the boundary of lexical environment and check
      // if there is an iteration statement in between declaration and boundary (is binding/class declared inside iteration statement)

      val container = getEnclosingBlockScopeContainer(symbol.valueDeclaration)
      val usedInFunction = isInsideFunction(node.parent, container)
      var current = container

      var containedInIterationStatement = false
      while (current && !nodeStartsNewLexicalEnvironment(current)) {
        if (isIterationStatement(current, /*lookInLabeledStatements*/ false)) {
          containedInIterationStatement = true
          break
        }
        current = current.parent
      }

      if (containedInIterationStatement) {
        if (usedInFunction) {
          // mark iteration statement as containing block-scoped binding captured in some def
          getNodeLinks(current).flags |= NodeCheckFlags.LoopWithCapturedBlockScopedBinding
        }

        // mark variables that are declared in loop initializer and reassigned inside the body of ForStatement.
        // if body of ForStatement will be converted to def then we'll need a extra machinery to propagate reassigned values back.
        if (container.kind == SyntaxKind.ForStatement &&
          getAncestor(symbol.valueDeclaration, SyntaxKind.VariableDeclarationList).parent == container &&
          isAssignedInBodyOfForStatement(node, <ForStatement>container)) {
          getNodeLinks(symbol.valueDeclaration).flags |= NodeCheckFlags.NeedsLoopOutParameter
        }

        // set 'declared inside loop' bit on the block-scoped binding
        getNodeLinks(symbol.valueDeclaration).flags |= NodeCheckFlags.BlockScopedBindingInLoop
      }

      if (usedInFunction) {
        getNodeLinks(symbol.valueDeclaration).flags |= NodeCheckFlags.CapturedBlockScopedBinding
      }
    }

    def isAssignedInBodyOfForStatement(node: Identifier, container: ForStatement): Boolean {
      var current: Node = node
      // skip parenthesized nodes
      while (current.parent.kind == SyntaxKind.ParenthesizedExpression) {
        current = current.parent
      }

      // check if node is used as LHS in some assignment expression
      var isAssigned = false
      if (current.parent.kind == SyntaxKind.BinaryExpression) {
        isAssigned = (<BinaryExpression>current.parent).left == current && isAssignmentOperator((<BinaryExpression>current.parent).operatorToken.kind)
      }

      if ((current.parent.kind == SyntaxKind.PrefixUnaryExpression || current.parent.kind == SyntaxKind.PostfixUnaryExpression)) {
        val expr = <PrefixUnaryExpression | PostfixUnaryExpression>current.parent
        isAssigned = expr.operator == SyntaxKind.PlusPlusToken || expr.operator == SyntaxKind.MinusMinusToken
      }

      if (!isAssigned) {
        return false
      }

      // at this point we know that node is the target of assignment
      // now check that modification happens inside the statement part of the ForStatement
      while (current != container) {
        if (current == container.statement) {
          return true
        }
        else {
          current = current.parent
        }
      }
      return false
    }

    def captureLexicalThis(node: Node, container: Node): Unit {
      getNodeLinks(node).flags |= NodeCheckFlags.LexicalThis
      if (container.kind == SyntaxKind.PropertyDeclaration || container.kind == SyntaxKind.Constructor) {
        val classNode = container.parent
        getNodeLinks(classNode).flags |= NodeCheckFlags.CaptureThis
      }
      else {
        getNodeLinks(container).flags |= NodeCheckFlags.CaptureThis
      }
    }

    def checkThisExpression(node: Node): Type {
      // Stop at the first arrow def so that we can
      // tell whether 'this' needs to be captured.
      var container = getThisContainer(node, /* includeArrowFunctions */ true)
      var needToCaptureLexicalThis = false

      if (container.kind == SyntaxKind.Constructor) {
        val baseTypeNode = getClassExtendsHeritageClauseElement(<ClassLikeDeclaration>container.parent)
        if (baseTypeNode && !(getNodeCheckFlags(container) & NodeCheckFlags.HasSeenSuperCall)) {
          // In ES6, super inside constructor of class-declaration has to precede "this" accessing
          error(node, Diagnostics.super_must_be_called_before_accessing_this_in_the_constructor_of_a_derived_class)
        }
      }

      // Now skip arrow functions to get the "real" owner of 'this'.
      if (container.kind == SyntaxKind.ArrowFunction) {
        container = getThisContainer(container, /* includeArrowFunctions */ false)

        // When targeting es6, arrow def lexically bind "this" so we do not need to do the work of binding "this" in emitted code
        needToCaptureLexicalThis = (languageVersion < ScriptTarget.ES6)
      }

      switch (container.kind) {
        case SyntaxKind.ModuleDeclaration:
          error(node, Diagnostics.this_cannot_be_referenced_in_a_module_or_namespace_body)
          // do not return here so in case if lexical this is captured - it will be reflected in flags on NodeLinks
          break
        case SyntaxKind.EnumDeclaration:
          error(node, Diagnostics.this_cannot_be_referenced_in_current_location)
          // do not return here so in case if lexical this is captured - it will be reflected in flags on NodeLinks
          break
        case SyntaxKind.Constructor:
          if (isInConstructorArgumentInitializer(node, container)) {
            error(node, Diagnostics.this_cannot_be_referenced_in_constructor_arguments)
            // do not return here so in case if lexical this is captured - it will be reflected in flags on NodeLinks
          }
          break
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
          if (container.flags & NodeFlags.Static) {
            error(node, Diagnostics.this_cannot_be_referenced_in_a_static_property_initializer)
            // do not return here so in case if lexical this is captured - it will be reflected in flags on NodeLinks
          }
          break
        case SyntaxKind.ComputedPropertyName:
          error(node, Diagnostics.this_cannot_be_referenced_in_a_computed_property_name)
          break
      }

      if (needToCaptureLexicalThis) {
        captureLexicalThis(node, container)
      }

      if (isClassLike(container.parent)) {
        val symbol = getSymbolOfNode(container.parent)
        return container.flags & NodeFlags.Static ? getTypeOfSymbol(symbol) : (<InterfaceType>getDeclaredTypeOfSymbol(symbol)).thisType
      }

      if (isInJavaScriptFile(node)) {
        val type = getTypeForThisExpressionFromJSDoc(container)
        if (type && type != unknownType) {
          return type
        }

        // If this is a def in a JS file, it might be a class method. Check if it's the RHS
        // of a x.prototype.y = def [name]() { .... }
        if (container.kind == SyntaxKind.FunctionExpression) {
          if (getSpecialPropertyAssignmentKind(container.parent) == SpecialPropertyAssignmentKind.PrototypeProperty) {
            // Get the 'x' of 'x.prototype.y = f' (here, 'f' is 'container')
            val className = (((container.parent as BinaryExpression)   // x.prototype.y = f
              .left as PropertyAccessExpression)     // x.prototype.y
              .expression as PropertyAccessExpression) // x.prototype
              .expression;               // x
            val classSymbol = checkExpression(className).symbol
            if (classSymbol && classSymbol.members && (classSymbol.flags & SymbolFlags.Function)) {
              return getInferredClassType(classSymbol)
            }
          }
        }
      }

      return anyType
    }

    def getTypeForThisExpressionFromJSDoc(node: Node) {
      val typeTag = getJSDocTypeTag(node)
      if (typeTag && typeTag.typeExpression && typeTag.typeExpression.type && typeTag.typeExpression.type.kind == SyntaxKind.JSDocFunctionType) {
        val jsDocFunctionType = <JSDocFunctionType>typeTag.typeExpression.type
        if (jsDocFunctionType.parameters.length > 0 && jsDocFunctionType.parameters[0].type.kind == SyntaxKind.JSDocThisType) {
          return getTypeFromTypeNode(jsDocFunctionType.parameters[0].type)
        }
      }
    }

    def isInConstructorArgumentInitializer(node: Node, constructorDecl: Node): Boolean {
      for (var n = node; n && n != constructorDecl; n = n.parent) {
        if (n.kind == SyntaxKind.Parameter) {
          return true
        }
      }
      return false
    }

    def checkSuperExpression(node: Node): Type {
      val isCallExpression = node.parent.kind == SyntaxKind.CallExpression && (<CallExpression>node.parent).expression == node

      var container = getSuperContainer(node, /*stopOnFunctions*/ true)
      var needToCaptureLexicalThis = false

      if (!isCallExpression) {
        // adjust the container reference in case if super is used inside arrow functions with arbitrary deep nesting
        while (container && container.kind == SyntaxKind.ArrowFunction) {
          container = getSuperContainer(container, /*stopOnFunctions*/ true)
          needToCaptureLexicalThis = languageVersion < ScriptTarget.ES6
        }
      }

      val canUseSuperExpression = isLegalUsageOfSuperExpression(container)
      var nodeCheckFlag: NodeCheckFlags = 0

      if (!canUseSuperExpression) {
        // issue more specific error if super is used in computed property name
        // class A { foo() { return "1" }}
        // class B {
        //   [super.foo()]() {}
        // }
        var current = node
        while (current && current != container && current.kind != SyntaxKind.ComputedPropertyName) {
          current = current.parent
        }
        if (current && current.kind == SyntaxKind.ComputedPropertyName) {
          error(node, Diagnostics.super_cannot_be_referenced_in_a_computed_property_name)
        }
        else if (isCallExpression) {
          error(node, Diagnostics.Super_calls_are_not_permitted_outside_constructors_or_in_nested_functions_inside_constructors)
        }
        else if (!container || !container.parent || !(isClassLike(container.parent) || container.parent.kind == SyntaxKind.ObjectLiteralExpression)) {
          error(node, Diagnostics.super_can_only_be_referenced_in_members_of_derived_classes_or_object_literal_expressions)
        }
        else {
          error(node, Diagnostics.super_property_access_is_permitted_only_in_a_constructor_member_function_or_member_accessor_of_a_derived_class)
        }
        return unknownType
      }

      if ((container.flags & NodeFlags.Static) || isCallExpression) {
        nodeCheckFlag = NodeCheckFlags.SuperStatic
      }
      else {
        nodeCheckFlag = NodeCheckFlags.SuperInstance
      }

      getNodeLinks(node).flags |= nodeCheckFlag

      // Due to how we emit async functions, we need to specialize the emit for an async method that contains a `super` reference.
      // This is due to the fact that we emit the body of an async def inside of a generator def. As generator
      // functions cannot reference `super`, we emit a helper inside of the method body, but outside of the generator. This helper
      // uses an arrow def, which is permitted to reference `super`.
      //
      // There are two primary ways we can access `super` from within an async method. The first is getting the value of a property
      // or indexed access on super, either as part of a right-hand-side expression or call expression. The second is when setting the value
      // of a property or indexed access, either as part of an assignment expression or destructuring assignment.
      //
      // The simplest case is reading a value, in which case we will emit something like the following:
      //
      //  // ts
      //  ...
      //  async asyncMethod() {
      //  var x = await super.asyncMethod()
      //  return x
      //  }
      //  ...
      //
      //  // js
      //  ...
      //  asyncMethod() {
      //    val _super = name => super[name]
      //    return __awaiter(this, arguments, Promise, def *() {
      //      var x = yield _super("asyncMethod").call(this)
      //      return x
      //    })
      //  }
      //  ...
      //
      // The more complex case is when we wish to assign a value, especially as part of a destructuring assignment. As both cases
      // are legal in ES6, but also likely less frequent, we emit the same more complex helper for both scenarios:
      //
      //  // ts
      //  ...
      //  async asyncMethod(ar: Promise<any[]>) {
      //    [super.a, super.b] = await ar
      //  }
      //  ...
      //
      //  // js
      //  ...
      //  asyncMethod(ar) {
      //    val _super = (def (geti, seti) {
      //      val cache = Object.create(null)
      //      return name => cache[name] || (cache[name] = { get value() { return geti(name); }, set value(v) { seti(name, v); } })
      //    })(name => super[name], (name, value) => super[name] = value)
      //    return __awaiter(this, arguments, Promise, def *() {
      //      [_super("a").value, _super("b").value] = yield ar
      //    })
      //  }
      //  ...
      //
      // This helper creates an object with a "value" property that wraps the `super` property or indexed access for both get and set.
      // This is required for destructuring assignments, as a call expression cannot be used as the target of a destructuring assignment
      // while a property access can.
      if (container.kind == SyntaxKind.MethodDeclaration && container.flags & NodeFlags.Async) {
        if (isSuperPropertyOrElementAccess(node.parent) && isAssignmentTarget(node.parent)) {
          getNodeLinks(container).flags |= NodeCheckFlags.AsyncMethodWithSuperBinding
        }
        else {
          getNodeLinks(container).flags |= NodeCheckFlags.AsyncMethodWithSuper
        }
      }

      if (needToCaptureLexicalThis) {
        // call expressions are allowed only in constructors so they should always capture correct 'this'
        // super property access expressions can also appear in arrow functions -
        // in this case they should also use correct lexical this
        captureLexicalThis(node.parent, container)
      }

      if (container.parent.kind == SyntaxKind.ObjectLiteralExpression) {
        if (languageVersion < ScriptTarget.ES6) {
          error(node, Diagnostics.super_is_only_allowed_in_members_of_object_literal_expressions_when_option_target_is_ES2015_or_higher)
          return unknownType
        }
        else {
          // for object literal assume that type of 'super' is 'any'
          return anyType
        }
      }

      // at this point the only legal case for parent is ClassLikeDeclaration
      val classLikeDeclaration = <ClassLikeDeclaration>container.parent
      val classType = <InterfaceType>getDeclaredTypeOfSymbol(getSymbolOfNode(classLikeDeclaration))
      val baseClassType = classType && getBaseTypes(classType)[0]
      if (!baseClassType) {
        if (!getClassExtendsHeritageClauseElement(classLikeDeclaration)) {
          error(node, Diagnostics.super_can_only_be_referenced_in_a_derived_class)
        }
        return unknownType
      }

      if (container.kind == SyntaxKind.Constructor && isInConstructorArgumentInitializer(node, container)) {
        // issue custom error message for super property access in constructor arguments (to be aligned with old compiler)
        error(node, Diagnostics.super_cannot_be_referenced_in_constructor_arguments)
        return unknownType
      }

      return nodeCheckFlag == NodeCheckFlags.SuperStatic
        ? getBaseConstructorTypeOfClass(classType)
        : baseClassType

      def isLegalUsageOfSuperExpression(container: Node): Boolean {
        if (!container) {
          return false
        }

        if (isCallExpression) {
          // TS 1.0 SPEC (April 2014): 4.8.1
          // Super calls are only permitted in constructors of derived classes
          return container.kind == SyntaxKind.Constructor
        }
        else {
          // TS 1.0 SPEC (April 2014)
          // 'super' property access is allowed
          // - In a constructor, instance member def, instance member accessor, or instance member variable initializer where this references a derived class instance
          // - In a static member def or static member accessor

          // topmost container must be something that is directly nested in the class declaration\object literal expression
          if (isClassLike(container.parent) || container.parent.kind == SyntaxKind.ObjectLiteralExpression) {
            if (container.flags & NodeFlags.Static) {
              return container.kind == SyntaxKind.MethodDeclaration ||
                container.kind == SyntaxKind.MethodSignature ||
                container.kind == SyntaxKind.GetAccessor ||
                container.kind == SyntaxKind.SetAccessor
            }
            else {
              return container.kind == SyntaxKind.MethodDeclaration ||
                container.kind == SyntaxKind.MethodSignature ||
                container.kind == SyntaxKind.GetAccessor ||
                container.kind == SyntaxKind.SetAccessor ||
                container.kind == SyntaxKind.PropertyDeclaration ||
                container.kind == SyntaxKind.PropertySignature ||
                container.kind == SyntaxKind.Constructor
            }
          }
        }

        return false
      }
    }

    // Return contextual type of parameter or () if no contextual type is available
    def getContextuallyTypedParameterType(parameter: ParameterDeclaration): Type {
      val func = parameter.parent
      if (isFunctionExpressionOrArrowFunction(func) || isObjectLiteralMethod(func)) {
        if (isContextSensitive(func)) {
          val contextualSignature = getContextualSignature(func)
          if (contextualSignature) {

            val funcHasRestParameters = hasRestParameter(func)
            val len = func.parameters.length - (funcHasRestParameters ? 1 : 0)
            val indexOfParameter = indexOf(func.parameters, parameter)
            if (indexOfParameter < len) {
              return getTypeAtPosition(contextualSignature, indexOfParameter)
            }

            // If last parameter is contextually rest parameter get its type
            if (funcHasRestParameters &&
              indexOfParameter == (func.parameters.length - 1) &&
              isRestParameterIndex(contextualSignature, func.parameters.length - 1)) {
              return getTypeOfSymbol(lastOrUndefined(contextualSignature.parameters))
            }
          }
        }
      }
      return ()
    }

    // In a variable, parameter or property declaration with a type annotation, the contextual type of an initializer
    // expression is the type of the variable, parameter or property. Otherwise, in a parameter declaration of a
    // contextually typed def expression, the contextual type of an initializer expression is the contextual type
    // of the parameter. Otherwise, in a variable or parameter declaration with a binding pattern name, the contextual
    // type of an initializer expression is the type implied by the binding pattern.
    def getContextualTypeForInitializerExpression(node: Expression): Type {
      val declaration = <VariableLikeDeclaration>node.parent
      if (node == declaration.initializer) {
        if (declaration.type) {
          return getTypeFromTypeNode(declaration.type)
        }
        if (declaration.kind == SyntaxKind.Parameter) {
          val type = getContextuallyTypedParameterType(<ParameterDeclaration>declaration)
          if (type) {
            return type
          }
        }
        if (isBindingPattern(declaration.name)) {
          return getTypeFromBindingPattern(<BindingPattern>declaration.name, /*includePatternInType*/ true)
        }
      }
      return ()
    }

    def getContextualTypeForReturnExpression(node: Expression): Type {
      val func = getContainingFunction(node)
      if (func && !func.asteriskToken) {
        return getContextualReturnType(func)
      }

      return ()
    }

    def getContextualTypeForYieldOperand(node: YieldExpression): Type {
      val func = getContainingFunction(node)
      if (func) {
        val contextualReturnType = getContextualReturnType(func)
        if (contextualReturnType) {
          return node.asteriskToken
            ? contextualReturnType
            : getElementTypeOfIterableIterator(contextualReturnType)
        }
      }

      return ()
    }

    def isInParameterInitializerBeforeContainingFunction(node: Node) {
      while (node.parent && !isFunctionLike(node.parent)) {
        if (node.parent.kind == SyntaxKind.Parameter && (<ParameterDeclaration>node.parent).initializer == node) {
          return true
        }

        node = node.parent
      }

      return false
    }

    def getContextualReturnType(functionDecl: FunctionLikeDeclaration): Type {
      // If the containing def has a return type annotation, is a constructor, or is a get accessor whose
      // corresponding set accessor has a type annotation, return statements in the def are contextually typed
      if (functionDecl.type ||
        functionDecl.kind == SyntaxKind.Constructor ||
        functionDecl.kind == SyntaxKind.GetAccessor && getSetAccessorTypeAnnotationNode(<AccessorDeclaration>getDeclarationOfKind(functionDecl.symbol, SyntaxKind.SetAccessor))) {
        return getReturnTypeOfSignature(getSignatureFromDeclaration(functionDecl))
      }

      // Otherwise, if the containing def is contextually typed by a def type with exactly one call signature
      // and that call signature is non-generic, return statements are contextually typed by the return type of the signature
      val signature = getContextualSignatureForFunctionLikeDeclaration(<FunctionExpression>functionDecl)
      if (signature) {
        return getReturnTypeOfSignature(signature)
      }

      return ()
    }

    // In a typed def call, an argument or substitution expression is contextually typed by the type of the corresponding parameter.
    def getContextualTypeForArgument(callTarget: CallLikeExpression, arg: Expression): Type {
      val args = getEffectiveCallArguments(callTarget)
      val argIndex = indexOf(args, arg)
      if (argIndex >= 0) {
        val signature = getResolvedSignature(callTarget)
        return getTypeAtPosition(signature, argIndex)
      }
      return ()
    }

    def getContextualTypeForSubstitutionExpression(template: TemplateExpression, substitutionExpression: Expression) {
      if (template.parent.kind == SyntaxKind.TaggedTemplateExpression) {
        return getContextualTypeForArgument(<TaggedTemplateExpression>template.parent, substitutionExpression)
      }

      return ()
    }

    def getContextualTypeForBinaryOperand(node: Expression): Type {
      val binaryExpression = <BinaryExpression>node.parent
      val operator = binaryExpression.operatorToken.kind
      if (operator >= SyntaxKind.FirstAssignment && operator <= SyntaxKind.LastAssignment) {
        // In an assignment expression, the right operand is contextually typed by the type of the left operand.
        if (node == binaryExpression.right) {
          return checkExpression(binaryExpression.left)
        }
      }
      else if (operator == SyntaxKind.BarBarToken) {
        // When an || expression has a contextual type, the operands are contextually typed by that type. When an ||
        // expression has no contextual type, the right operand is contextually typed by the type of the left operand.
        var type = getContextualType(binaryExpression)
        if (!type && node == binaryExpression.right) {
          type = checkExpression(binaryExpression.left)
        }
        return type
      }
      else if (operator == SyntaxKind.AmpersandAmpersandToken || operator == SyntaxKind.CommaToken) {
        if (node == binaryExpression.right) {
          return getContextualType(binaryExpression)
        }
      }

      return ()
    }

    // Apply a mapping def to a contextual type and return the resulting type. If the contextual type
    // is a union type, the mapping def is applied to each constituent type and a union of the resulting
    // types is returned.
    def applyToContextualType(type: Type, mapper: (t: Type) => Type): Type {
      if (!(type.flags & TypeFlags.Union)) {
        return mapper(type)
      }
      val types = (<UnionType>type).types
      var mappedType: Type
      var mappedTypes: Type[]
      for (val current of types) {
        val t = mapper(current)
        if (t) {
          if (!mappedType) {
            mappedType = t
          }
          else if (!mappedTypes) {
            mappedTypes = [mappedType, t]
          }
          else {
            mappedTypes.push(t)
          }
        }
      }
      return mappedTypes ? getUnionType(mappedTypes) : mappedType
    }

    def getTypeOfPropertyOfContextualType(type: Type, name: String) {
      return applyToContextualType(type, t => {
        val prop = t.flags & TypeFlags.StructuredType ? getPropertyOfType(t, name) : ()
        return prop ? getTypeOfSymbol(prop) : ()
      })
    }

    def getIndexTypeOfContextualType(type: Type, kind: IndexKind) {
      return applyToContextualType(type, t => getIndexTypeOfStructuredType(t, kind))
    }

    def contextualTypeIsStringLiteralType(type: Type): Boolean {
      return !!(type.flags & TypeFlags.Union ? forEach((<UnionType>type).types, isStringLiteralType) : isStringLiteralType(type))
    }

    // Return true if the given contextual type is a tuple-like type
    def contextualTypeIsTupleLikeType(type: Type): Boolean {
      return !!(type.flags & TypeFlags.Union ? forEach((<UnionType>type).types, isTupleLikeType) : isTupleLikeType(type))
    }

    // Return true if the given contextual type provides an index signature of the given kind
    def contextualTypeHasIndexSignature(type: Type, kind: IndexKind): Boolean {
      return !!(type.flags & TypeFlags.Union ? forEach((<UnionType>type).types, t => getIndexInfoOfStructuredType(t, kind)) : getIndexInfoOfStructuredType(type, kind))
    }

    // In an object literal contextually typed by a type T, the contextual type of a property assignment is the type of
    // the matching property in T, if one exists. Otherwise, it is the type of the numeric index signature in T, if one
    // exists. Otherwise, it is the type of the String index signature in T, if one exists.
    def getContextualTypeForObjectLiteralMethod(node: MethodDeclaration): Type {
      Debug.assert(isObjectLiteralMethod(node))
      if (isInsideWithStatementBody(node)) {
        // We cannot answer semantic questions within a with block, do not proceed any further
        return ()
      }

      return getContextualTypeForObjectLiteralElement(node)
    }

    def getContextualTypeForObjectLiteralElement(element: ObjectLiteralElement) {
      val objectLiteral = <ObjectLiteralExpression>element.parent
      val type = getApparentTypeOfContextualType(objectLiteral)
      if (type) {
        if (!hasDynamicName(element)) {
          // For a (non-symbol) computed property, there is no reason to look up the name
          // in the type. It will just be "__computed", which does not appear in any
          // SymbolTable.
          val symbolName = getSymbolOfNode(element).name
          val propertyType = getTypeOfPropertyOfContextualType(type, symbolName)
          if (propertyType) {
            return propertyType
          }
        }

        return isNumericName(element.name) && getIndexTypeOfContextualType(type, IndexKind.Number) ||
          getIndexTypeOfContextualType(type, IndexKind.String)
      }

      return ()
    }

    // In an array literal contextually typed by a type T, the contextual type of an element expression at index N is
    // the type of the property with the numeric name N in T, if one exists. Otherwise, if T has a numeric index signature,
    // it is the type of the numeric index signature in T. Otherwise, in ES6 and higher, the contextual type is the iterated
    // type of T.
    def getContextualTypeForElementExpression(node: Expression): Type {
      val arrayLiteral = <ArrayLiteralExpression>node.parent
      val type = getApparentTypeOfContextualType(arrayLiteral)
      if (type) {
        val index = indexOf(arrayLiteral.elements, node)
        return getTypeOfPropertyOfContextualType(type, "" + index)
          || getIndexTypeOfContextualType(type, IndexKind.Number)
          || (languageVersion >= ScriptTarget.ES6 ? getElementTypeOfIterable(type, /*errorNode*/ ()) : ())
      }
      return ()
    }

    // In a contextually typed conditional expression, the true/false expressions are contextually typed by the same type.
    def getContextualTypeForConditionalOperand(node: Expression): Type {
      val conditional = <ConditionalExpression>node.parent
      return node == conditional.whenTrue || node == conditional.whenFalse ? getContextualType(conditional) : ()
    }

    def getContextualTypeForJsxAttribute(attribute: JsxAttribute | JsxSpreadAttribute) {
      val kind = attribute.kind
      val jsxElement = attribute.parent as JsxOpeningLikeElement
      val attrsType = getJsxElementAttributesType(jsxElement)

      if (attribute.kind == SyntaxKind.JsxAttribute) {
        if (!attrsType || isTypeAny(attrsType)) {
          return ()
        }
        return getTypeOfPropertyOfType(attrsType, (attribute as JsxAttribute).name.text)
      }
      else if (attribute.kind == SyntaxKind.JsxSpreadAttribute) {
        return attrsType
      }

      Debug.fail(`Expected JsxAttribute or JsxSpreadAttribute, got ts.SyntaxKind[${kind}]`)
    }

    // Return the contextual type for a given expression node. During overload resolution, a contextual type may temporarily
    // be "pushed" onto a node using the contextualType property.
    def getApparentTypeOfContextualType(node: Expression): Type {
      val type = getContextualType(node)
      return type && getApparentType(type)
    }

    /**
     * Woah! Do you really want to use this def?
     *
     * Unless you're trying to get the *non-apparent* type for a
     * value-literal type or you're authoring relevant portions of this algorithm,
     * you probably meant to use 'getApparentTypeOfContextualType'.
     * Otherwise this may not be very useful.
     *
     * In cases where you *are* working on this def, you should understand
     * when it is appropriate to use 'getContextualType' and 'getApparentTypeOfContextualType'.
     *
     *   - Use 'getContextualType' when you are simply going to propagate the result to the expression.
     *   - Use 'getApparentTypeOfContextualType' when you're going to need the members of the type.
     *
     * @param node the expression whose contextual type will be returned.
     * @returns the contextual type of an expression.
     */
    def getContextualType(node: Expression): Type {
      if (isInsideWithStatementBody(node)) {
        // We cannot answer semantic questions within a with block, do not proceed any further
        return ()
      }
      if (node.contextualType) {
        return node.contextualType
      }
      val parent = node.parent
      switch (parent.kind) {
        case SyntaxKind.VariableDeclaration:
        case SyntaxKind.Parameter:
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
        case SyntaxKind.BindingElement:
          return getContextualTypeForInitializerExpression(node)
        case SyntaxKind.ArrowFunction:
        case SyntaxKind.ReturnStatement:
          return getContextualTypeForReturnExpression(node)
        case SyntaxKind.YieldExpression:
          return getContextualTypeForYieldOperand(<YieldExpression>parent)
        case SyntaxKind.CallExpression:
        case SyntaxKind.NewExpression:
          return getContextualTypeForArgument(<CallExpression>parent, node)
        case SyntaxKind.TypeAssertionExpression:
        case SyntaxKind.AsExpression:
          return getTypeFromTypeNode((<AssertionExpression>parent).type)
        case SyntaxKind.BinaryExpression:
          return getContextualTypeForBinaryOperand(node)
        case SyntaxKind.PropertyAssignment:
          return getContextualTypeForObjectLiteralElement(<ObjectLiteralElement>parent)
        case SyntaxKind.ArrayLiteralExpression:
          return getContextualTypeForElementExpression(node)
        case SyntaxKind.ConditionalExpression:
          return getContextualTypeForConditionalOperand(node)
        case SyntaxKind.TemplateSpan:
          Debug.assert(parent.parent.kind == SyntaxKind.TemplateExpression)
          return getContextualTypeForSubstitutionExpression(<TemplateExpression>parent.parent, node)
        case SyntaxKind.ParenthesizedExpression:
          return getContextualType(<ParenthesizedExpression>parent)
        case SyntaxKind.JsxExpression:
          return getContextualType(<JsxExpression>parent)
        case SyntaxKind.JsxAttribute:
        case SyntaxKind.JsxSpreadAttribute:
          return getContextualTypeForJsxAttribute(<JsxAttribute | JsxSpreadAttribute>parent)
      }
      return ()
    }

    // If the given type is an object or union type, if that type has a single signature, and if
    // that signature is non-generic, return the signature. Otherwise return ().
    def getNonGenericSignature(type: Type): Signature {
      val signatures = getSignaturesOfStructuredType(type, SignatureKind.Call)
      if (signatures.length == 1) {
        val signature = signatures[0]
        if (!signature.typeParameters) {
          return signature
        }
      }
    }

    def isFunctionExpressionOrArrowFunction(node: Node): node is FunctionExpression {
      return node.kind == SyntaxKind.FunctionExpression || node.kind == SyntaxKind.ArrowFunction
    }

    def getContextualSignatureForFunctionLikeDeclaration(node: FunctionLikeDeclaration): Signature {
      // Only def expressions, arrow functions, and object literal methods are contextually typed.
      return isFunctionExpressionOrArrowFunction(node) || isObjectLiteralMethod(node)
        ? getContextualSignature(<FunctionExpression>node)
        : ()
    }

    // Return the contextual signature for a given expression node. A contextual type provides a
    // contextual signature if it has a single call signature and if that call signature is non-generic.
    // If the contextual type is a union type, get the signature from each type possible and if they are
    // all identical ignoring their return type, the result is same signature but with return type as
    // union type of return types from these signatures
    def getContextualSignature(node: FunctionExpression | MethodDeclaration): Signature {
      Debug.assert(node.kind != SyntaxKind.MethodDeclaration || isObjectLiteralMethod(node))
      val type = isObjectLiteralMethod(node)
        ? getContextualTypeForObjectLiteralMethod(node)
        : getApparentTypeOfContextualType(node)
      if (!type) {
        return ()
      }
      if (!(type.flags & TypeFlags.Union)) {
        return getNonGenericSignature(type)
      }
      var signatureList: Signature[]
      val types = (<UnionType>type).types
      for (val current of types) {
        val signature = getNonGenericSignature(current)
        if (signature) {
          if (!signatureList) {
            // This signature will contribute to contextual union signature
            signatureList = [signature]
          }
          else if (!compareSignaturesIdentical(signatureList[0], signature, /*partialMatch*/ false, /*ignoreReturnTypes*/ true, compareTypesIdentical)) {
            // Signatures aren't identical, do not use
            return ()
          }
          else {
            // Use this signature for contextual union signature
            signatureList.push(signature)
          }
        }
      }

      // Result is union of signatures collected (return type is union of return types of this signature set)
      var result: Signature
      if (signatureList) {
        result = cloneSignature(signatureList[0])
        // Clear resolved return type we possibly got from cloneSignature
        result.resolvedReturnType = ()
        result.unionSignatures = signatureList
      }
      return result
    }

    /**
     * Detect if the mapper implies an inference context. Specifically, there are 4 possible values
     * for a mapper. Let's go through each one of them:
     *
     *  1. () - this means we are not doing inferential typing, but we may do contextual typing,
     *     which could cause us to assign a parameter a type
     *  2. identityMapper - means we want to avoid assigning a parameter a type, whether or not we are in
     *     inferential typing (context is () for the identityMapper)
     *  3. a mapper created by createInferenceMapper - we are doing inferential typing, we want to assign
     *     types to parameters and fix type parameters (context is defined)
     *  4. an instantiation mapper created by createTypeMapper or createTypeEraser - this should never be
     *     passed as the contextual mapper when checking an expression (context is () for these)
     *
     * isInferentialContext is detecting if we are in case 3
     */
    def isInferentialContext(mapper: TypeMapper) {
      return mapper && mapper.context
    }

    // A node is an assignment target if it is on the left hand side of an '=' token, if it is parented by a property
    // assignment in an object literal that is an assignment target, or if it is parented by an array literal that is
    // an assignment target. Examples include 'a = xxx', '{ p: a } = xxx', '[{ p: a}] = xxx'.
    def isAssignmentTarget(node: Node): Boolean {
      val parent = node.parent
      if (parent.kind == SyntaxKind.BinaryExpression && (<BinaryExpression>parent).operatorToken.kind == SyntaxKind.EqualsToken && (<BinaryExpression>parent).left == node) {
        return true
      }
      if (parent.kind == SyntaxKind.PropertyAssignment) {
        return isAssignmentTarget(parent.parent)
      }
      if (parent.kind == SyntaxKind.ArrayLiteralExpression) {
        return isAssignmentTarget(parent)
      }
      return false
    }

    def checkSpreadElementExpression(node: SpreadElementExpression, contextualMapper?: TypeMapper): Type {
      // It is usually not safe to call checkExpressionCached if we can be contextually typing.
      // You can tell that we are contextually typing because of the contextualMapper parameter.
      // While it is true that a spread element can have a contextual type, it does not do anything
      // with this type. It is neither affected by it, nor does it propagate it to its operand.
      // So the fact that contextualMapper is passed is not important, because the operand of a spread
      // element is not contextually typed.
      val arrayOrIterableType = checkExpressionCached(node.expression, contextualMapper)
      return checkIteratedTypeOrElementType(arrayOrIterableType, node.expression, /*allowStringInput*/ false)
    }

    def hasDefaultValue(node: BindingElement | Expression): Boolean {
      return (node.kind == SyntaxKind.BindingElement && !!(<BindingElement>node).initializer) ||
        (node.kind == SyntaxKind.BinaryExpression && (<BinaryExpression>node).operatorToken.kind == SyntaxKind.EqualsToken)
    }

    def checkArrayLiteral(node: ArrayLiteralExpression, contextualMapper?: TypeMapper): Type {
      val elements = node.elements
      var hasSpreadElement = false
      val elementTypes: Type[] = []
      val inDestructuringPattern = isAssignmentTarget(node)
      for (val e of elements) {
        if (inDestructuringPattern && e.kind == SyntaxKind.SpreadElementExpression) {
          // Given the following situation:
          //  var c: {}
          //  [...c] = ["", 0]
          //
          // c is represented in the tree as a spread element in an array literal.
          // But c really functions as a rest element, and its purpose is to provide
          // a contextual type for the right hand side of the assignment. Therefore,
          // instead of calling checkExpression on "...c", which will give an error
          // if c is not iterable/array-like, we need to act as if we are trying to
          // get the contextual element type from it. So we do something similar to
          // getContextualTypeForElementExpression, which will crucially not error
          // if there is no index type / iterated type.
          val restArrayType = checkExpression((<SpreadElementExpression>e).expression, contextualMapper)
          val restElementType = getIndexTypeOfType(restArrayType, IndexKind.Number) ||
            (languageVersion >= ScriptTarget.ES6 ? getElementTypeOfIterable(restArrayType, /*errorNode*/ ()) : ())
          if (restElementType) {
            elementTypes.push(restElementType)
          }
        }
        else {
          val type = checkExpression(e, contextualMapper)
          elementTypes.push(type)
        }
        hasSpreadElement = hasSpreadElement || e.kind == SyntaxKind.SpreadElementExpression
      }
      if (!hasSpreadElement) {
        // If array literal is actually a destructuring pattern, mark it as an implied type. We do this such
        // that we get the same behavior for "var [x, y] = []" and "[x, y] = []".
        if (inDestructuringPattern && elementTypes.length) {
          val type = createNewTupleType(elementTypes)
          type.pattern = node
          return type
        }
        val contextualType = getApparentTypeOfContextualType(node)
        if (contextualType && contextualTypeIsTupleLikeType(contextualType)) {
          val pattern = contextualType.pattern
          // If array literal is contextually typed by a binding pattern or an assignment pattern, pad the resulting
          // tuple type with the corresponding binding or assignment element types to make the lengths equal.
          if (pattern && (pattern.kind == SyntaxKind.ArrayBindingPattern || pattern.kind == SyntaxKind.ArrayLiteralExpression)) {
            val patternElements = (<BindingPattern | ArrayLiteralExpression>pattern).elements
            for (var i = elementTypes.length; i < patternElements.length; i++) {
              val patternElement = patternElements[i]
              if (hasDefaultValue(patternElement)) {
                elementTypes.push((<TupleType>contextualType).elementTypes[i])
              }
              else {
                if (patternElement.kind != SyntaxKind.OmittedExpression) {
                  error(patternElement, Diagnostics.Initializer_provides_no_value_for_this_binding_element_and_the_binding_element_has_no_default_value)
                }
                elementTypes.push(unknownType)
              }
            }
          }
          if (elementTypes.length) {
            return createTupleType(elementTypes)
          }
        }
      }
      return createArrayType(elementTypes.length ? getUnionType(elementTypes) : undefinedType)
    }

    def isNumericName(name: DeclarationName): Boolean {
      return name.kind == SyntaxKind.ComputedPropertyName ? isNumericComputedName(<ComputedPropertyName>name) : isNumericLiteralName((<Identifier>name).text)
    }

    def isNumericComputedName(name: ComputedPropertyName): Boolean {
      // It seems odd to consider an expression of type Any to result in a numeric name,
      // but this behavior is consistent with checkIndexedAccess
      return isTypeAnyOrAllConstituentTypesHaveKind(checkComputedPropertyName(name), TypeFlags.NumberLike)
    }

    def isTypeAnyOrAllConstituentTypesHaveKind(type: Type, kind: TypeFlags): Boolean {
      return isTypeAny(type) || isTypeOfKind(type, kind)
    }

    def isNumericLiteralName(name: String) {
      // The intent of numeric names is that
      //   - they are names with text in a numeric form, and that
      //   - setting properties/indexing with them is always equivalent to doing so with the numeric literal 'numLit',
      //     acquired by applying the abstract 'ToNumber' operation on the name's text.
      //
      // The subtlety is in the latter portion, as we cannot reliably say that anything that looks like a numeric literal is a numeric name.
      // In fact, it is the case that the text of the name must be equal to 'ToString(numLit)' for this to hold.
      //
      // Consider the property name '"0xF00D"'. When one indexes with '0xF00D', they are actually indexing with the value of 'ToString(0xF00D)'
      // according to the ECMAScript specification, so it is actually as if the user indexed with the String '"61453"'.
      // Thus, the text of all numeric literals equivalent to '61543' such as '0xF00D', '0xf00D', '0170015', etc. are not valid numeric names
      // because their 'ToString' representation is not equal to their original text.
      // This is motivated by ECMA-262 sections 9.3.1, 9.8.1, 11.1.5, and 11.2.1.
      //
      // Here, we test whether 'ToString(ToNumber(name))' is exactly equal to 'name'.
      // The '+' prefix operator is equivalent here to applying the abstract ToNumber operation.
      // Applying the 'toString()' method on a Int gives us the abstract ToString operation on a Int.
      //
      // Note that this accepts the values 'Infinity', '-Infinity', and 'NaN', and that this is intentional.
      // This is desired behavior, because when indexing with them as numeric entities, you are indexing
      // with the strings '"Infinity"', '"-Infinity"', and '"NaN"' respectively.
      return (+name).toString() == name
    }

    def checkComputedPropertyName(node: ComputedPropertyName): Type {
      val links = getNodeLinks(node.expression)
      if (!links.resolvedType) {
        links.resolvedType = checkExpression(node.expression)

        // This will allow types Int, String, symbol or any. It will also allow enums, the unknown
        // type, and any union of these types (like String | Int).
        if (!isTypeAnyOrAllConstituentTypesHaveKind(links.resolvedType, TypeFlags.NumberLike | TypeFlags.StringLike | TypeFlags.ESSymbol)) {
          error(node, Diagnostics.A_computed_property_name_must_be_of_type_string_number_symbol_or_any)
        }
        else {
          checkThatExpressionIsProperSymbolReference(node.expression, links.resolvedType, /*reportError*/ true)
        }
      }

      return links.resolvedType
    }

    def checkObjectLiteral(node: ObjectLiteralExpression, contextualMapper?: TypeMapper): Type {
      val inDestructuringPattern = isAssignmentTarget(node)
      // Grammar checking
      checkGrammarObjectLiteralExpression(node, inDestructuringPattern)

      val propertiesTable: SymbolTable = {}
      val propertiesArray: Symbol[] = []
      val contextualType = getApparentTypeOfContextualType(node)
      val contextualTypeHasPattern = contextualType && contextualType.pattern &&
        (contextualType.pattern.kind == SyntaxKind.ObjectBindingPattern || contextualType.pattern.kind == SyntaxKind.ObjectLiteralExpression)
      var typeFlags: TypeFlags = 0

      var patternWithComputedProperties = false
      for (val memberDecl of node.properties) {
        var member = memberDecl.symbol
        if (memberDecl.kind == SyntaxKind.PropertyAssignment ||
          memberDecl.kind == SyntaxKind.ShorthandPropertyAssignment ||
          isObjectLiteralMethod(memberDecl)) {
          var type: Type
          if (memberDecl.kind == SyntaxKind.PropertyAssignment) {
            type = checkPropertyAssignment(<PropertyAssignment>memberDecl, contextualMapper)
          }
          else if (memberDecl.kind == SyntaxKind.MethodDeclaration) {
            type = checkObjectLiteralMethod(<MethodDeclaration>memberDecl, contextualMapper)
          }
          else {
            Debug.assert(memberDecl.kind == SyntaxKind.ShorthandPropertyAssignment)
            type = checkExpression((<ShorthandPropertyAssignment>memberDecl).name, contextualMapper)
          }
          typeFlags |= type.flags
          val prop = <TransientSymbol>createSymbol(SymbolFlags.Property | SymbolFlags.Transient | member.flags, member.name)
          if (inDestructuringPattern) {
            // If object literal is an assignment pattern and if the assignment pattern specifies a default value
            // for the property, make the property optional.
            val isOptional =
              (memberDecl.kind == SyntaxKind.PropertyAssignment && hasDefaultValue((<PropertyAssignment>memberDecl).initializer)) ||
              (memberDecl.kind == SyntaxKind.ShorthandPropertyAssignment && (<ShorthandPropertyAssignment>memberDecl).objectAssignmentInitializer)
            if (isOptional) {
              prop.flags |= SymbolFlags.Optional
            }
            if (hasDynamicName(memberDecl)) {
              patternWithComputedProperties = true
            }
          }
          else if (contextualTypeHasPattern && !(contextualType.flags & TypeFlags.ObjectLiteralPatternWithComputedProperties)) {
            // If object literal is contextually typed by the implied type of a binding pattern, and if the
            // binding pattern specifies a default value for the property, make the property optional.
            val impliedProp = getPropertyOfType(contextualType, member.name)
            if (impliedProp) {
              prop.flags |= impliedProp.flags & SymbolFlags.Optional
            }
            else if (!compilerOptions.suppressExcessPropertyErrors) {
              error(memberDecl.name, Diagnostics.Object_literal_may_only_specify_known_properties_and_0_does_not_exist_in_type_1,
                symbolToString(member), typeToString(contextualType))
            }
          }
          prop.declarations = member.declarations
          prop.parent = member.parent
          if (member.valueDeclaration) {
            prop.valueDeclaration = member.valueDeclaration
          }

          prop.type = type
          prop.target = member
          member = prop
        }
        else {
          // TypeScript 1.0 spec (April 2014)
          // A get accessor declaration is processed in the same manner as
          // an ordinary def declaration(section 6.1) with no parameters.
          // A set accessor declaration is processed in the same manner
          // as an ordinary def declaration with a single parameter and a Void return type.
          Debug.assert(memberDecl.kind == SyntaxKind.GetAccessor || memberDecl.kind == SyntaxKind.SetAccessor)
          checkAccessorDeclaration(<AccessorDeclaration>memberDecl)
        }

        if (!hasDynamicName(memberDecl)) {
          propertiesTable[member.name] = member
        }
        propertiesArray.push(member)
      }

      // If object literal is contextually typed by the implied type of a binding pattern, augment the result
      // type with those properties for which the binding pattern specifies a default value.
      if (contextualTypeHasPattern) {
        for (val prop of getPropertiesOfType(contextualType)) {
          if (!hasProperty(propertiesTable, prop.name)) {
            if (!(prop.flags & SymbolFlags.Optional)) {
              error(prop.valueDeclaration || (<TransientSymbol>prop).bindingElement,
                Diagnostics.Initializer_provides_no_value_for_this_binding_element_and_the_binding_element_has_no_default_value)
            }
            propertiesTable[prop.name] = prop
            propertiesArray.push(prop)
          }
        }
      }

      val stringIndexInfo = getIndexInfo(IndexKind.String)
      val numberIndexInfo = getIndexInfo(IndexKind.Number)
      val result = createAnonymousType(node.symbol, propertiesTable, emptyArray, emptyArray, stringIndexInfo, numberIndexInfo)
      val freshObjectLiteralFlag = compilerOptions.suppressExcessPropertyErrors ? 0 : TypeFlags.FreshObjectLiteral
      result.flags |= TypeFlags.ObjectLiteral | TypeFlags.ContainsObjectLiteral | freshObjectLiteralFlag | (typeFlags & TypeFlags.PropagatingFlags) | (patternWithComputedProperties ? TypeFlags.ObjectLiteralPatternWithComputedProperties : 0)
      if (inDestructuringPattern) {
        result.pattern = node
      }
      return result

      def getIndexInfo(kind: IndexKind) {
        if (contextualType && contextualTypeHasIndexSignature(contextualType, kind)) {
          val propTypes: Type[] = []
          for (var i = 0; i < propertiesArray.length; i++) {
            val propertyDecl = node.properties[i]
            if (kind == IndexKind.String || isNumericName(propertyDecl.name)) {
              // Do not call getSymbolOfNode(propertyDecl), as that will get the
              // original symbol for the node. We actually want to get the symbol
              // created by checkObjectLiteral, since that will be appropriately
              // contextually typed and resolved.
              val type = getTypeOfSymbol(propertiesArray[i])
              if (!contains(propTypes, type)) {
                propTypes.push(type)
              }
            }
          }
          val unionType = propTypes.length ? getUnionType(propTypes) : undefinedType
          typeFlags |= unionType.flags
          return createIndexInfo(unionType, /*isReadonly*/ false)
        }
        return ()
      }
    }

    def checkJsxSelfClosingElement(node: JsxSelfClosingElement) {
      checkJsxOpeningLikeElement(node)
      return jsxElementType || anyType
    }

    def checkJsxElement(node: JsxElement) {
      // Check attributes
      checkJsxOpeningLikeElement(node.openingElement)

      // Perform resolution on the closing tag so that rename/go to definition/etc work
      getJsxTagSymbol(node.closingElement)

      // Check children
      for (val child of node.children) {
        switch (child.kind) {
          case SyntaxKind.JsxExpression:
            checkJsxExpression(<JsxExpression>child)
            break
          case SyntaxKind.JsxElement:
            checkJsxElement(<JsxElement>child)
            break
          case SyntaxKind.JsxSelfClosingElement:
            checkJsxSelfClosingElement(<JsxSelfClosingElement>child)
            break
        }
      }

      return jsxElementType || anyType
    }

    /**
     * Returns true iff the JSX element name would be a valid JS identifier, ignoring restrictions about keywords not being identifiers
     */
    def isUnhyphenatedJsxName(name: String) {
      // - is the only character supported in JSX attribute names that isn't valid in JavaScript identifiers
      return name.indexOf("-") < 0
    }

    /**
     * Returns true iff React would emit this tag name as a String rather than an identifier or qualified name
     */
    def isJsxIntrinsicIdentifier(tagName: Identifier | QualifiedName) {
      if (tagName.kind == SyntaxKind.QualifiedName) {
        return false
      }
      else {
        return isIntrinsicJsxName((<Identifier>tagName).text)
      }
    }

    def checkJsxAttribute(node: JsxAttribute, elementAttributesType: Type, nameTable: Map<Boolean>) {
      var correspondingPropType: Type = ()

      // Look up the corresponding property for this attribute
      if (elementAttributesType == emptyObjectType && isUnhyphenatedJsxName(node.name.text)) {
        // If there is no 'props' property, you may not have non-"data-" attributes
        error(node.parent, Diagnostics.JSX_element_class_does_not_support_attributes_because_it_does_not_have_a_0_property, getJsxElementPropertiesName())
      }
      else if (elementAttributesType && !isTypeAny(elementAttributesType)) {
        val correspondingPropSymbol = getPropertyOfType(elementAttributesType, node.name.text)
        correspondingPropType = correspondingPropSymbol && getTypeOfSymbol(correspondingPropSymbol)
        if (isUnhyphenatedJsxName(node.name.text)) {
          // Maybe there's a String indexer?
          val indexerType = getIndexTypeOfType(elementAttributesType, IndexKind.String)
          if (indexerType) {
            correspondingPropType = indexerType
          }
          else {
            // If there's no corresponding property with this name, error
            if (!correspondingPropType) {
              error(node.name, Diagnostics.Property_0_does_not_exist_on_type_1, node.name.text, typeToString(elementAttributesType))
              return unknownType
            }
          }
        }
      }

      var exprType: Type
      if (node.initializer) {
        exprType = checkExpression(node.initializer)
      }
      else {
        // <Elem attr /> is sugar for <Elem attr={true} />
        exprType = booleanType
      }

      if (correspondingPropType) {
        checkTypeAssignableTo(exprType, correspondingPropType, node)
      }

      nameTable[node.name.text] = true
      return exprType
    }

    def checkJsxSpreadAttribute(node: JsxSpreadAttribute, elementAttributesType: Type, nameTable: Map<Boolean>) {
      val type = checkExpression(node.expression)
      val props = getPropertiesOfType(type)
      for (val prop of props) {
        // Is there a corresponding property in the element attributes type? Skip checking of properties
        // that have already been assigned to, as these are not actually pushed into the resulting type
        if (!nameTable[prop.name]) {
          val targetPropSym = getPropertyOfType(elementAttributesType, prop.name)
          if (targetPropSym) {
            val msg = chainDiagnosticMessages((), Diagnostics.Property_0_of_JSX_spread_attribute_is_not_assignable_to_target_property, prop.name)
            checkTypeAssignableTo(getTypeOfSymbol(prop), getTypeOfSymbol(targetPropSym), node, (), msg)
          }

          nameTable[prop.name] = true
        }
      }
      return type
    }

    def getJsxType(name: String) {
      if (jsxTypes[name] == ()) {
        return jsxTypes[name] = getExportedTypeFromNamespace(JsxNames.JSX, name) || unknownType
      }
      return jsxTypes[name]
    }

    def getJsxTagSymbol(node: JsxOpeningLikeElement | JsxClosingElement): Symbol {
      if (isJsxIntrinsicIdentifier(node.tagName)) {
        return getIntrinsicTagSymbol(node)
      }
      else {
        return checkExpression(node.tagName).symbol
      }
    }

    /**
      * Looks up an intrinsic tag name and returns a symbol that either points to an intrinsic
      * property (in which case nodeLinks.jsxFlags will be IntrinsicNamedElement) or an intrinsic
      * String index signature (in which case nodeLinks.jsxFlags will be IntrinsicIndexedElement).
      * May also return unknownSymbol if both of these lookups fail.
      */
    def getIntrinsicTagSymbol(node: JsxOpeningLikeElement | JsxClosingElement): Symbol {
      val links = getNodeLinks(node)
      if (!links.resolvedSymbol) {
        val intrinsicElementsType = getJsxType(JsxNames.IntrinsicElements)
        if (intrinsicElementsType != unknownType) {
          // Property case
          val intrinsicProp = getPropertyOfType(intrinsicElementsType, (<Identifier>node.tagName).text)
          if (intrinsicProp) {
            links.jsxFlags |= JsxFlags.IntrinsicNamedElement
            return links.resolvedSymbol = intrinsicProp
          }

          // Intrinsic String indexer case
          val indexSignatureType = getIndexTypeOfType(intrinsicElementsType, IndexKind.String)
          if (indexSignatureType) {
            links.jsxFlags |= JsxFlags.IntrinsicIndexedElement
            return links.resolvedSymbol = intrinsicElementsType.symbol
          }

          // Wasn't found
          error(node, Diagnostics.Property_0_does_not_exist_on_type_1, (<Identifier>node.tagName).text, "JSX." + JsxNames.IntrinsicElements)
          return links.resolvedSymbol = unknownSymbol
        }
        else {
          if (compilerOptions.noImplicitAny) {
            error(node, Diagnostics.JSX_element_implicitly_has_type_any_because_no_interface_JSX_0_exists, JsxNames.IntrinsicElements)
          }
          return links.resolvedSymbol = unknownSymbol
        }
      }
      return links.resolvedSymbol
    }

    /**
     * Given a JSX element that is a class element, finds the Element Instance Type. If the
     * element is not a class element, or the class element type cannot be determined, returns '()'.
     * For example, in the element <MyClass>, the element instance type is `MyClass` (not `typeof MyClass`).
     */
    def getJsxElementInstanceType(node: JsxOpeningLikeElement) {
      val valueType = checkExpression(node.tagName)

      if (isTypeAny(valueType)) {
        // Short-circuit if the class tag is using an element type 'any'
        return anyType
      }

      // Resolve the signatures, preferring constructors
      var signatures = getSignaturesOfType(valueType, SignatureKind.Construct)
      if (signatures.length == 0) {
        // No construct signatures, try call signatures
        signatures = getSignaturesOfType(valueType, SignatureKind.Call)

        if (signatures.length == 0) {
          // We found no signatures at all, which is an error
          error(node.tagName, Diagnostics.JSX_element_type_0_does_not_have_any_construct_or_call_signatures, getTextOfNode(node.tagName))
          return unknownType
        }
      }

      return getUnionType(signatures.map(getReturnTypeOfSignature))
    }

    /// e.g. "props" for React.d.ts,
    /// or '()' if ElementAttributesProperty doesn't exist (which means all
    ///   non-intrinsic elements' attributes type is 'any'),
    /// or '' if it has 0 properties (which means every
    ///   non-intrinsic elements' attributes type is the element instance type)
    def getJsxElementPropertiesName() {
      // JSX
      val jsxNamespace = getGlobalSymbol(JsxNames.JSX, SymbolFlags.Namespace, /*diagnosticMessage*/())
      // JSX.ElementAttributesProperty [symbol]
      val attribsPropTypeSym = jsxNamespace && getSymbol(jsxNamespace.exports, JsxNames.ElementAttributesPropertyNameContainer, SymbolFlags.Type)
      // JSX.ElementAttributesProperty [type]
      val attribPropType = attribsPropTypeSym && getDeclaredTypeOfSymbol(attribsPropTypeSym)
      // The properties of JSX.ElementAttributesProperty
      val attribProperties = attribPropType && getPropertiesOfType(attribPropType)

      if (attribProperties) {
        // Element Attributes has zero properties, so the element attributes type will be the class instance type
        if (attribProperties.length == 0) {
          return ""
        }
        // Element Attributes has one property, so the element attributes type will be the type of the corresponding
        // property of the class instance type
        else if (attribProperties.length == 1) {
          return attribProperties[0].name
        }
        // More than one property on ElementAttributesProperty is an error
        else {
          error(attribsPropTypeSym.declarations[0], Diagnostics.The_global_type_JSX_0_may_not_have_more_than_one_property, JsxNames.ElementAttributesPropertyNameContainer)
          return ()
        }
      }
      else {
        // No trait exists, so the element attributes type will be an implicit any
        return ()
      }
    }

    /**
     * Given an opening/self-closing element, get the 'element attributes type', i.e. the type that tells
     * us which attributes are valid on a given element.
     */
    def getJsxElementAttributesType(node: JsxOpeningLikeElement): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedJsxType) {
        if (isJsxIntrinsicIdentifier(node.tagName)) {
          val symbol = getIntrinsicTagSymbol(node)
          if (links.jsxFlags & JsxFlags.IntrinsicNamedElement) {
            return links.resolvedJsxType = getTypeOfSymbol(symbol)
          }
          else if (links.jsxFlags & JsxFlags.IntrinsicIndexedElement) {
            return links.resolvedJsxType = getIndexInfoOfSymbol(symbol, IndexKind.String).type
          }
        }
        else {
          // Get the element instance type (the result of newing or invoking this tag)
          val elemInstanceType = getJsxElementInstanceType(node)

          val elemClassType = getJsxGlobalElementClassType()

          if (!elemClassType || !isTypeAssignableTo(elemInstanceType, elemClassType)) {
            // Is this is a stateless def component? See if its single signature's return type is
            // assignable to the JSX Element Type
            val elemType = checkExpression(node.tagName)
            val callSignatures = elemType && getSignaturesOfType(elemType, SignatureKind.Call)
            val callSignature = callSignatures && callSignatures.length > 0 && callSignatures[0]
            val callReturnType = callSignature && getReturnTypeOfSignature(callSignature)
            var paramType = callReturnType && (callSignature.parameters.length == 0 ? emptyObjectType : getTypeOfSymbol(callSignature.parameters[0]))
            if (callReturnType && isTypeAssignableTo(callReturnType, jsxElementType)) {
              // Intersect in JSX.IntrinsicAttributes if it exists
              val intrinsicAttributes = getJsxType(JsxNames.IntrinsicAttributes)
              if (intrinsicAttributes != unknownType) {
                paramType = intersectTypes(intrinsicAttributes, paramType)
              }
              return links.resolvedJsxType = paramType
            }
          }

          // Issue an error if this return type isn't assignable to JSX.ElementClass
          if (elemClassType) {
            checkTypeRelatedTo(elemInstanceType, elemClassType, assignableRelation, node, Diagnostics.JSX_element_type_0_is_not_a_constructor_function_for_JSX_elements)
          }

          if (isTypeAny(elemInstanceType)) {
            return links.resolvedJsxType = elemInstanceType
          }

          val propsName = getJsxElementPropertiesName()
          if (propsName == ()) {
            // There is no type ElementAttributesProperty, return 'any'
            return links.resolvedJsxType = anyType
          }
          else if (propsName == "") {
            // If there is no e.g. 'props' member in ElementAttributesProperty, use the element class type instead
            return links.resolvedJsxType = elemInstanceType
          }
          else {
            val attributesType = getTypeOfPropertyOfType(elemInstanceType, propsName)

            if (!attributesType) {
              // There is no property named 'props' on this instance type
              return links.resolvedJsxType = emptyObjectType
            }
            else if (isTypeAny(attributesType) || (attributesType == unknownType)) {
              // Props is of type 'any' or unknown
              return links.resolvedJsxType = attributesType
            }
            else if (attributesType.flags & TypeFlags.Union) {
              // Props cannot be a union type
              error(node.tagName, Diagnostics.JSX_element_attributes_type_0_may_not_be_a_union_type, typeToString(attributesType))
              return links.resolvedJsxType = anyType
            }
            else {
              // Normal case -- add in IntrinsicClassElements<T> and IntrinsicElements
              var apparentAttributesType = attributesType
              val intrinsicClassAttribs = getJsxType(JsxNames.IntrinsicClassAttributes)
              if (intrinsicClassAttribs != unknownType) {
                val typeParams = getLocalTypeParametersOfClassOrInterfaceOrTypeAlias(intrinsicClassAttribs.symbol)
                if (typeParams) {
                  if (typeParams.length == 1) {
                    apparentAttributesType = intersectTypes(createTypeReference(<GenericType>intrinsicClassAttribs, [elemInstanceType]), apparentAttributesType)
                  }
                }
                else {
                  apparentAttributesType = intersectTypes(attributesType, intrinsicClassAttribs)
                }
              }

              val intrinsicAttribs = getJsxType(JsxNames.IntrinsicAttributes)
              if (intrinsicAttribs != unknownType) {
                apparentAttributesType = intersectTypes(intrinsicAttribs, apparentAttributesType)
              }

              return links.resolvedJsxType = apparentAttributesType
            }
          }
        }

        return links.resolvedJsxType = unknownType
      }

      return links.resolvedJsxType
    }

    /**
     * Given a JSX attribute, returns the symbol for the corresponds property
     * of the element attributes type. Will return unknownSymbol for attributes
     * that have no matching element attributes type property.
     */
    def getJsxAttributePropertySymbol(attrib: JsxAttribute): Symbol {
      val attributesType = getJsxElementAttributesType(<JsxOpeningElement>attrib.parent)
      val prop = getPropertyOfType(attributesType, attrib.name.text)
      return prop || unknownSymbol
    }

    def getJsxGlobalElementClassType(): Type {
      if (!jsxElementClassType) {
        jsxElementClassType = getExportedTypeFromNamespace(JsxNames.JSX, JsxNames.ElementClass)
      }
      return jsxElementClassType
    }

    /// Returns all the properties of the Jsx.IntrinsicElements trait
    def getJsxIntrinsicTagNames(): Symbol[] {
      val intrinsics = getJsxType(JsxNames.IntrinsicElements)
      return intrinsics ? getPropertiesOfType(intrinsics) : emptyArray
    }

    def checkJsxPreconditions(errorNode: Node) {
      // Preconditions for using JSX
      if ((compilerOptions.jsx || JsxEmit.None) == JsxEmit.None) {
        error(errorNode, Diagnostics.Cannot_use_JSX_unless_the_jsx_flag_is_provided)
      }

      if (jsxElementType == ()) {
        if (compilerOptions.noImplicitAny) {
          error(errorNode, Diagnostics.JSX_element_implicitly_has_type_any_because_the_global_type_JSX_Element_does_not_exist)
        }
      }
    }

    def checkJsxOpeningLikeElement(node: JsxOpeningLikeElement) {
      checkGrammarJsxElement(node)
      checkJsxPreconditions(node)

      // The reactNamespace symbol should be marked as 'used' so we don't incorrectly elide its import. And if there
      // is no reactNamespace symbol in scope when targeting React emit, we should issue an error.
      val reactRefErr = compilerOptions.jsx == JsxEmit.React ? Diagnostics.Cannot_find_name_0 : ()
      val reactNamespace = compilerOptions.reactNamespace ? compilerOptions.reactNamespace : "React"
      val reactSym = resolveName(node.tagName, reactNamespace, SymbolFlags.Value, reactRefErr, reactNamespace)
      if (reactSym) {
        getSymbolLinks(reactSym).referenced = true
      }

      val targetAttributesType = getJsxElementAttributesType(node)

      val nameTable: Map<Boolean> = {}
      // Process this array in right-to-left order so we know which
      // attributes (mostly from spreads) are being overwritten and
      // thus should have their types ignored
      var sawSpreadedAny = false
      for (var i = node.attributes.length - 1; i >= 0; i--) {
        if (node.attributes[i].kind == SyntaxKind.JsxAttribute) {
          checkJsxAttribute(<JsxAttribute>(node.attributes[i]), targetAttributesType, nameTable)
        }
        else {
          Debug.assert(node.attributes[i].kind == SyntaxKind.JsxSpreadAttribute)
          val spreadType = checkJsxSpreadAttribute(<JsxSpreadAttribute>(node.attributes[i]), targetAttributesType, nameTable)
          if (isTypeAny(spreadType)) {
            sawSpreadedAny = true
          }
        }
      }

      // Check that all required properties have been provided. If an 'any'
      // was spreaded in, though, assume that it provided all required properties
      if (targetAttributesType && !sawSpreadedAny) {
        val targetProperties = getPropertiesOfType(targetAttributesType)
        for (var i = 0; i < targetProperties.length; i++) {
          if (!(targetProperties[i].flags & SymbolFlags.Optional) &&
            nameTable[targetProperties[i].name] == ()) {

            error(node, Diagnostics.Property_0_is_missing_in_type_1, targetProperties[i].name, typeToString(targetAttributesType))
          }
        }
      }
    }

    def checkJsxExpression(node: JsxExpression) {
      if (node.expression) {
        return checkExpression(node.expression)
      }
      else {
        return unknownType
      }
    }

    // If a symbol is a synthesized symbol with no value declaration, we assume it is a property. Example of this are the synthesized
    // '.prototype' property as well as synthesized tuple index properties.
    def getDeclarationKindFromSymbol(s: Symbol) {
      return s.valueDeclaration ? s.valueDeclaration.kind : SyntaxKind.PropertyDeclaration
    }

    def getDeclarationFlagsFromSymbol(s: Symbol): NodeFlags {
      return s.valueDeclaration ? getCombinedNodeFlags(s.valueDeclaration) : s.flags & SymbolFlags.Prototype ? NodeFlags.Public | NodeFlags.Static : 0
    }

    /**
     * Check whether the requested property access is valid.
     * Returns true if node is a valid property access, and false otherwise.
     * @param node The node to be checked.
     * @param left The left hand side of the property access (e.g.: the super in `super.foo`).
     * @param type The type of left.
     * @param prop The symbol for the right hand side of the property access.
     */
    def checkClassPropertyAccess(node: PropertyAccessExpression | QualifiedName, left: Expression | QualifiedName, type: Type, prop: Symbol): Boolean {
      val flags = getDeclarationFlagsFromSymbol(prop)
      val declaringClass = <InterfaceType>getDeclaredTypeOfSymbol(getParentOfSymbol(prop))

      if (left.kind == SyntaxKind.SuperKeyword) {
        val errorNode = node.kind == SyntaxKind.PropertyAccessExpression ?
          (<PropertyAccessExpression>node).name :
          (<QualifiedName>node).right

        // TS 1.0 spec (April 2014): 4.8.2
        // - In a constructor, instance member def, instance member accessor, or
        //   instance member variable initializer where this references a derived class instance,
        //   a super property access is permitted and must specify a public instance member def of the base class.
        // - In a static member def or static member accessor
        //   where this references the constructor def object of a derived class,
        //   a super property access is permitted and must specify a public static member def of the base class.
        if (languageVersion < ScriptTarget.ES6 && getDeclarationKindFromSymbol(prop) != SyntaxKind.MethodDeclaration) {
          // `prop` refers to a *property* declared in the super class
          // rather than a *method*, so it does not satisfy the above criteria.

          error(errorNode, Diagnostics.Only_public_and_protected_methods_of_the_base_class_are_accessible_via_the_super_keyword)
          return false
        }

        if (flags & NodeFlags.Abstract) {
          // A method cannot be accessed in a super property access if the method is abstract.
          // This error could mask a private property access error. But, a member
          // cannot simultaneously be private and abstract, so this will trigger an
          // additional error elsewhere.

          error(errorNode, Diagnostics.Abstract_method_0_in_class_1_cannot_be_accessed_via_super_expression, symbolToString(prop), typeToString(declaringClass))
          return false
        }
      }

      // Public properties are otherwise accessible.
      if (!(flags & (NodeFlags.Private | NodeFlags.Protected))) {
        return true
      }

      // Property is known to be private or protected at this point
      // Get the declaring and enclosing class instance types
      val enclosingClassDeclaration = getContainingClass(node)

      val enclosingClass = enclosingClassDeclaration ? <InterfaceType>getDeclaredTypeOfSymbol(getSymbolOfNode(enclosingClassDeclaration)) : ()

      // Private property is accessible if declaring and enclosing class are the same
      if (flags & NodeFlags.Private) {
        if (declaringClass != enclosingClass) {
          error(node, Diagnostics.Property_0_is_private_and_only_accessible_within_class_1, symbolToString(prop), typeToString(declaringClass))
          return false
        }
        return true
      }

      // Property is known to be protected at this point

      // All protected properties of a supertype are accessible in a super access
      if (left.kind == SyntaxKind.SuperKeyword) {
        return true
      }
      // A protected property is accessible in the declaring class and classes derived from it
      if (!enclosingClass || !hasBaseType(enclosingClass, declaringClass)) {
        error(node, Diagnostics.Property_0_is_protected_and_only_accessible_within_class_1_and_its_subclasses, symbolToString(prop), typeToString(declaringClass))
        return false
      }
      // No further restrictions for static properties
      if (flags & NodeFlags.Static) {
        return true
      }
      // An instance property must be accessed through an instance of the enclosing class
      if (type.flags & TypeFlags.ThisType) {
        // get the original type -- represented as the type constraint of the 'this' type
        type = getConstraintOfTypeParameter(<TypeParameter>type)
      }

      // TODO: why is the first part of this check here?
      if (!(getTargetType(type).flags & (TypeFlags.Class | TypeFlags.Interface) && hasBaseType(<InterfaceType>type, enclosingClass))) {
        error(node, Diagnostics.Property_0_is_protected_and_only_accessible_through_an_instance_of_class_1, symbolToString(prop), typeToString(enclosingClass))
        return false
      }
      return true
    }

    def checkPropertyAccessExpression(node: PropertyAccessExpression) {
      return checkPropertyAccessExpressionOrQualifiedName(node, node.expression, node.name)
    }

    def checkQualifiedName(node: QualifiedName) {
      return checkPropertyAccessExpressionOrQualifiedName(node, node.left, node.right)
    }

    def checkPropertyAccessExpressionOrQualifiedName(node: PropertyAccessExpression | QualifiedName, left: Expression | QualifiedName, right: Identifier) {
      val type = checkExpression(left)
      if (isTypeAny(type)) {
        return type
      }

      val apparentType = getApparentType(getWidenedType(type))
      if (apparentType == unknownType) {
        // handle cases when type is Type parameter with invalid constraint
        return unknownType
      }
      val prop = getPropertyOfType(apparentType, right.text)
      if (!prop) {
        if (right.text) {
          error(right, Diagnostics.Property_0_does_not_exist_on_type_1, declarationNameToString(right), typeToString(type.flags & TypeFlags.ThisType ? apparentType : type))
        }
        return unknownType
      }

      getNodeLinks(node).resolvedSymbol = prop

      if (prop.parent && prop.parent.flags & SymbolFlags.Class) {
        checkClassPropertyAccess(node, left, apparentType, prop)
      }
      return getTypeOfSymbol(prop)
    }

    def isValidPropertyAccess(node: PropertyAccessExpression | QualifiedName, propertyName: String): Boolean {
      val left = node.kind == SyntaxKind.PropertyAccessExpression
        ? (<PropertyAccessExpression>node).expression
        : (<QualifiedName>node).left

      val type = checkExpression(left)
      if (type != unknownType && !isTypeAny(type)) {
        val prop = getPropertyOfType(getWidenedType(type), propertyName)
        if (prop && prop.parent && prop.parent.flags & SymbolFlags.Class) {
          return checkClassPropertyAccess(node, left, type, prop)
        }
      }
      return true
    }

    /**
     * Return the symbol of the for-in variable declared or referenced by the given for-in statement.
     */
    def getForInVariableSymbol(node: ForInStatement): Symbol {
      val initializer = node.initializer
      if (initializer.kind == SyntaxKind.VariableDeclarationList) {
        val variable = (<VariableDeclarationList>initializer).declarations[0]
        if (variable && !isBindingPattern(variable.name)) {
          return getSymbolOfNode(variable)
        }
      }
      else if (initializer.kind == SyntaxKind.Identifier) {
        return getResolvedSymbol(<Identifier>initializer)
      }
      return ()
    }

    /**
     * Return true if the given type is considered to have numeric property names.
     */
    def hasNumericPropertyNames(type: Type) {
      return getIndexTypeOfType(type, IndexKind.Number) && !getIndexTypeOfType(type, IndexKind.String)
    }

    /**
     * Return true if given node is an expression consisting of an identifier (possibly parenthesized)
     * that references a for-in variable for an object with numeric property names.
     */
    def isForInVariableForNumericPropertyNames(expr: Expression) {
      val e = skipParenthesizedNodes(expr)
      if (e.kind == SyntaxKind.Identifier) {
        val symbol = getResolvedSymbol(<Identifier>e)
        if (symbol.flags & SymbolFlags.Variable) {
          var child: Node = expr
          var node = expr.parent
          while (node) {
            if (node.kind == SyntaxKind.ForInStatement &&
              child == (<ForInStatement>node).statement &&
              getForInVariableSymbol(<ForInStatement>node) == symbol &&
              hasNumericPropertyNames(checkExpression((<ForInStatement>node).expression))) {
              return true
            }
            child = node
            node = node.parent
          }
        }
      }
      return false
    }

    def checkIndexedAccess(node: ElementAccessExpression): Type {
      // Grammar checking
      if (!node.argumentExpression) {
        val sourceFile = getSourceFileOfNode(node)
        if (node.parent.kind == SyntaxKind.NewExpression && (<NewExpression>node.parent).expression == node) {
          val start = skipTrivia(sourceFile.text, node.expression.end)
          val end = node.end
          grammarErrorAtPos(sourceFile, start, end - start, Diagnostics.new_T_cannot_be_used_to_create_an_array_Use_new_Array_T_instead)
        }
        else {
          val start = node.end - "]".length
          val end = node.end
          grammarErrorAtPos(sourceFile, start, end - start, Diagnostics.Expression_expected)
        }
      }

      // Obtain base constraint such that we can bail out if the constraint is an unknown type
      val objectType = getApparentType(checkExpression(node.expression))
      val indexType = node.argumentExpression ? checkExpression(node.argumentExpression) : unknownType

      if (objectType == unknownType) {
        return unknownType
      }

      val isConstEnum = isConstEnumObjectType(objectType)
      if (isConstEnum &&
        (!node.argumentExpression || node.argumentExpression.kind != SyntaxKind.StringLiteral)) {
        error(node.argumentExpression, Diagnostics.A_const_enum_member_can_only_be_accessed_using_a_string_literal)
        return unknownType
      }

      // TypeScript 1.0 spec (April 2014): 4.10 Property Access
      // - If IndexExpr is a String literal or a numeric literal and ObjExpr's apparent type has a property with the name
      //  given by that literal(converted to its String representation in the case of a numeric literal), the property access is of the type of that property.
      // - Otherwise, if ObjExpr's apparent type has a numeric index signature and IndexExpr is of type Any, the Number primitive type, or an enum type,
      //  the property access is of the type of that index signature.
      // - Otherwise, if ObjExpr's apparent type has a String index signature and IndexExpr is of type Any, the String or Number primitive type, or an enum type,
      //  the property access is of the type of that index signature.
      // - Otherwise, if IndexExpr is of type Any, the String or Number primitive type, or an enum type, the property access is of type Any.

      // See if we can index as a property.
      if (node.argumentExpression) {
        val name = getPropertyNameForIndexedAccess(node.argumentExpression, indexType)
        if (name != ()) {
          val prop = getPropertyOfType(objectType, name)
          if (prop) {
            getNodeLinks(node).resolvedSymbol = prop
            return getTypeOfSymbol(prop)
          }
          else if (isConstEnum) {
            error(node.argumentExpression, Diagnostics.Property_0_does_not_exist_on_const_enum_1, name, symbolToString(objectType.symbol))
            return unknownType
          }
        }
      }

      // Check for compatible indexer types.
      if (isTypeAnyOrAllConstituentTypesHaveKind(indexType, TypeFlags.StringLike | TypeFlags.NumberLike | TypeFlags.ESSymbol)) {

        // Try to use a Int indexer.
        if (isTypeAnyOrAllConstituentTypesHaveKind(indexType, TypeFlags.NumberLike) || isForInVariableForNumericPropertyNames(node.argumentExpression)) {
          val numberIndexInfo = getIndexInfoOfType(objectType, IndexKind.Number)
          if (numberIndexInfo) {
            getNodeLinks(node).resolvedIndexInfo = numberIndexInfo
            return numberIndexInfo.type
          }
        }

        // Try to use String indexing.
        val stringIndexInfo = getIndexInfoOfType(objectType, IndexKind.String)
        if (stringIndexInfo) {
          getNodeLinks(node).resolvedIndexInfo = stringIndexInfo
          return stringIndexInfo.type
        }

        // Fall back to any.
        if (compilerOptions.noImplicitAny && !compilerOptions.suppressImplicitAnyIndexErrors && !isTypeAny(objectType)) {
          error(node, getIndexTypeOfType(objectType, IndexKind.Number) ?
            Diagnostics.Element_implicitly_has_an_any_type_because_index_expression_is_not_of_type_number :
            Diagnostics.Index_signature_of_object_type_implicitly_has_an_any_type)
        }

        return anyType
      }

      // REVIEW: Users should know the type that was actually used.
      error(node, Diagnostics.An_index_expression_argument_must_be_of_type_string_number_symbol_or_any)

      return unknownType
    }

    /**
     * If indexArgumentExpression is a String literal or Int literal, returns its text.
     * If indexArgumentExpression is a constant value, returns its String value.
     * If indexArgumentExpression is a well known symbol, returns the property name corresponding
     *  to this symbol, as long as it is a proper symbol reference.
     * Otherwise, returns ().
     */
    def getPropertyNameForIndexedAccess(indexArgumentExpression: Expression, indexArgumentType: Type): String {
      if (indexArgumentExpression.kind == SyntaxKind.StringLiteral || indexArgumentExpression.kind == SyntaxKind.NumericLiteral) {
        return (<LiteralExpression>indexArgumentExpression).text
      }
      if (indexArgumentExpression.kind == SyntaxKind.ElementAccessExpression || indexArgumentExpression.kind == SyntaxKind.PropertyAccessExpression) {
        val value = getConstantValue(<ElementAccessExpression | PropertyAccessExpression>indexArgumentExpression)
        if (value != ()) {
          return value.toString()
        }
      }
      if (checkThatExpressionIsProperSymbolReference(indexArgumentExpression, indexArgumentType, /*reportError*/ false)) {
        val rightHandSideName = (<Identifier>(<PropertyAccessExpression>indexArgumentExpression).name).text
        return getPropertyNameForKnownSymbolName(rightHandSideName)
      }

      return ()
    }

    /**
     * A proper symbol reference requires the following:
     *   1. The property access denotes a property that exists
     *   2. The expression is of the form Symbol.<identifier>
     *   3. The property access is of the primitive type symbol.
     *   4. Symbol in this context resolves to the global Symbol object
     */
    def checkThatExpressionIsProperSymbolReference(expression: Expression, expressionType: Type, reportError: Boolean): Boolean {
      if (expressionType == unknownType) {
        // There is already an error, so no need to report one.
        return false
      }

      if (!isWellKnownSymbolSyntactically(expression)) {
        return false
      }

      // Make sure the property type is the primitive symbol type
      if ((expressionType.flags & TypeFlags.ESSymbol) == 0) {
        if (reportError) {
          error(expression, Diagnostics.A_computed_property_name_of_the_form_0_must_be_of_type_symbol, getTextOfNode(expression))
        }
        return false
      }

      // The name is Symbol.<someName>, so make sure Symbol actually resolves to the
      // global Symbol object
      val leftHandSide = <Identifier>(<PropertyAccessExpression>expression).expression
      val leftHandSideSymbol = getResolvedSymbol(leftHandSide)
      if (!leftHandSideSymbol) {
        return false
      }

      val globalESSymbol = getGlobalESSymbolConstructorSymbol()
      if (!globalESSymbol) {
        // Already errored when we tried to look up the symbol
        return false
      }

      if (leftHandSideSymbol != globalESSymbol) {
        if (reportError) {
          error(leftHandSide, Diagnostics.Symbol_reference_does_not_refer_to_the_global_Symbol_constructor_object)
        }
        return false
      }

      return true
    }

    def resolveUntypedCall(node: CallLikeExpression): Signature {
      if (node.kind == SyntaxKind.TaggedTemplateExpression) {
        checkExpression((<TaggedTemplateExpression>node).template)
      }
      else if (node.kind != SyntaxKind.Decorator) {
        forEach((<CallExpression>node).arguments, argument => {
          checkExpression(argument)
        })
      }
      return anySignature
    }

    def resolveErrorCall(node: CallLikeExpression): Signature {
      resolveUntypedCall(node)
      return unknownSignature
    }

    // Re-order candidate signatures into the result array. Assumes the result array to be empty.
    // The candidate list orders groups in reverse, but within a group signatures are kept in declaration order
    // A nit here is that we reorder only signatures that belong to the same symbol,
    // so order how inherited signatures are processed is still preserved.
    // trait A { (x: String): Unit }
    // trait B extends A { (x: 'foo'): String }
    // val b: B
    // b('foo') // <- here overloads should be processed as [(x:'foo'): String, (x: String): Unit]
    def reorderCandidates(signatures: Signature[], result: Signature[]): Unit {
      var lastParent: Node
      var lastSymbol: Symbol
      var cutoffIndex = 0
      var index: Int
      var specializedIndex = -1
      var spliceIndex: Int
      Debug.assert(!result.length)
      for (val signature of signatures) {
        val symbol = signature.declaration && getSymbolOfNode(signature.declaration)
        val parent = signature.declaration && signature.declaration.parent
        if (!lastSymbol || symbol == lastSymbol) {
          if (lastParent && parent == lastParent) {
            index++
          }
          else {
            lastParent = parent
            index = cutoffIndex
          }
        }
        else {
          // current declaration belongs to a different symbol
          // set cutoffIndex so re-orderings in the future won't change result set from 0 to cutoffIndex
          index = cutoffIndex = result.length
          lastParent = parent
        }
        lastSymbol = symbol

        // specialized signatures always need to be placed before non-specialized signatures regardless
        // of the cutoff position; see GH#1133
        if (signature.hasStringLiterals) {
          specializedIndex++
          spliceIndex = specializedIndex
          // The cutoff index always needs to be greater than or equal to the specialized signature index
          // in order to prevent non-specialized signatures from being added before a specialized
          // signature.
          cutoffIndex++
        }
        else {
          spliceIndex = index
        }

        result.splice(spliceIndex, 0, signature)
      }
    }

    def getSpreadArgumentIndex(args: Expression[]): Int {
      for (var i = 0; i < args.length; i++) {
        val arg = args[i]
        if (arg && arg.kind == SyntaxKind.SpreadElementExpression) {
          return i
        }
      }
      return -1
    }

    def hasCorrectArity(node: CallLikeExpression, args: Expression[], signature: Signature) {
      var adjustedArgCount: Int;      // Apparent Int of arguments we will have in this call
      var typeArguments: NodeArray<TypeNode>;  // Type arguments (() if none)
      var callIsIncomplete: Boolean;       // In incomplete call we want to be lenient when we have too few arguments
      var isDecorator: Boolean
      var spreadArgIndex = -1

      if (node.kind == SyntaxKind.TaggedTemplateExpression) {
        val tagExpression = <TaggedTemplateExpression>node

        // Even if the call is incomplete, we'll have a missing expression as our last argument,
        // so we can say the count is just the arg list length
        adjustedArgCount = args.length
        typeArguments = ()

        if (tagExpression.template.kind == SyntaxKind.TemplateExpression) {
          // If a tagged template expression lacks a tail literal, the call is incomplete.
          // Specifically, a template only can end in a TemplateTail or a Missing literal.
          val templateExpression = <TemplateExpression>tagExpression.template
          val lastSpan = lastOrUndefined(templateExpression.templateSpans)
          Debug.assert(lastSpan != ()); // we should always have at least one span.
          callIsIncomplete = nodeIsMissing(lastSpan.literal) || !!lastSpan.literal.isUnterminated
        }
        else {
          // If the template didn't end in a backtick, or its beginning occurred right prior to EOF,
          // then this might actually turn out to be a TemplateHead in the future
          // so we consider the call to be incomplete.
          val templateLiteral = <LiteralExpression>tagExpression.template
          Debug.assert(templateLiteral.kind == SyntaxKind.NoSubstitutionTemplateLiteral)
          callIsIncomplete = !!templateLiteral.isUnterminated
        }
      }
      else if (node.kind == SyntaxKind.Decorator) {
        isDecorator = true
        typeArguments = ()
        adjustedArgCount = getEffectiveArgumentCount(node, /*args*/ (), signature)
      }
      else {
        val callExpression = <CallExpression>node
        if (!callExpression.arguments) {
          // This only happens when we have something of the form: 'new C'
          Debug.assert(callExpression.kind == SyntaxKind.NewExpression)

          return signature.minArgumentCount == 0
        }

        // For IDE scenarios we may have an incomplete call, so a trailing comma is tantamount to adding another argument.
        adjustedArgCount = callExpression.arguments.hasTrailingComma ? args.length + 1 : args.length

        // If we are missing the close paren, the call is incomplete.
        callIsIncomplete = (<CallExpression>callExpression).arguments.end == callExpression.end

        typeArguments = callExpression.typeArguments
        spreadArgIndex = getSpreadArgumentIndex(args)
      }

      // If the user supplied type arguments, but the Int of type arguments does not match
      // the declared Int of type parameters, the call has an incorrect arity.
      val hasRightNumberOfTypeArgs = !typeArguments ||
        (signature.typeParameters && typeArguments.length == signature.typeParameters.length)
      if (!hasRightNumberOfTypeArgs) {
        return false
      }

      // If spread arguments are present, check that they correspond to a rest parameter. If so, no
      // further checking is necessary.
      if (spreadArgIndex >= 0) {
        return isRestParameterIndex(signature, spreadArgIndex)
      }

      // Too many arguments implies incorrect arity.
      if (!signature.hasRestParameter && adjustedArgCount > signature.parameters.length) {
        return false
      }

      // If the call is incomplete, we should skip the lower bound check.
      val hasEnoughArguments = adjustedArgCount >= signature.minArgumentCount
      return callIsIncomplete || hasEnoughArguments
    }

    // If type has a single call signature and no other members, return that signature. Otherwise, return ().
    def getSingleCallSignature(type: Type): Signature {
      if (type.flags & TypeFlags.ObjectType) {
        val resolved = resolveStructuredTypeMembers(<ObjectType>type)
        if (resolved.callSignatures.length == 1 && resolved.constructSignatures.length == 0 &&
          resolved.properties.length == 0 && !resolved.stringIndexInfo && !resolved.numberIndexInfo) {
          return resolved.callSignatures[0]
        }
      }
      return ()
    }

    // Instantiate a generic signature in the context of a non-generic signature (section 3.8.5 in TypeScript spec)
    def instantiateSignatureInContextOf(signature: Signature, contextualSignature: Signature, contextualMapper: TypeMapper): Signature {
      val context = createInferenceContext(signature.typeParameters, /*inferUnionTypes*/ true)
      forEachMatchingParameterType(contextualSignature, signature, (source, target) => {
        // Type parameters from outer context referenced by source type are fixed by instantiation of the source type
        inferTypes(context, instantiateType(source, contextualMapper), target)
      })
      return getSignatureInstantiation(signature, getInferredTypes(context))
    }

    def inferTypeArguments(node: CallLikeExpression, signature: Signature, args: Expression[], excludeArgument: Boolean[], context: InferenceContext): Unit {
      val typeParameters = signature.typeParameters
      val inferenceMapper = getInferenceMapper(context)

      // Clear out all the inference results from the last time inferTypeArguments was called on this context
      for (var i = 0; i < typeParameters.length; i++) {
        // As an optimization, we don't have to clear (and later recompute) inferred types
        // for type parameters that have already been fixed on the previous call to inferTypeArguments.
        // It would be just as correct to reset all of them. But then we'd be repeating the same work
        // for the type parameters that were fixed, namely the work done by getInferredType.
        if (!context.inferences[i].isFixed) {
          context.inferredTypes[i] = ()
        }
      }

      // On this call to inferTypeArguments, we may get more inferences for certain type parameters that were not
      // fixed last time. This means that a type parameter that failed inference last time may succeed this time,
      // or vice versa. Therefore, the failedTypeParameterIndex is useless if it points to an unfixed type parameter,
      // because it may change. So here we reset it. However, getInferredType will not revisit any type parameters
      // that were previously fixed. So if a fixed type parameter failed previously, it will fail again because
      // it will contain the exact same set of inferences. So if we reset the index from a fixed type parameter,
      // we will lose information that we won't recover this time around.
      if (context.failedTypeParameterIndex != () && !context.inferences[context.failedTypeParameterIndex].isFixed) {
        context.failedTypeParameterIndex = ()
      }

      // We perform two passes over the arguments. In the first pass we infer from all arguments, but use
      // wildcards for all context sensitive def expressions.
      val argCount = getEffectiveArgumentCount(node, args, signature)
      for (var i = 0; i < argCount; i++) {
        val arg = getEffectiveArgument(node, args, i)
        // If the effective argument is '()', then it is an argument that is present but is synthetic.
        if (arg == () || arg.kind != SyntaxKind.OmittedExpression) {
          val paramType = getTypeAtPosition(signature, i)
          var argType = getEffectiveArgumentType(node, i, arg)

          // If the effective argument type is '()', there is no synthetic type
          // for the argument. In that case, we should check the argument.
          if (argType == ()) {
            // For context sensitive arguments we pass the identityMapper, which is a signal to treat all
            // context sensitive def expressions as wildcards
            val mapper = excludeArgument && excludeArgument[i] != () ? identityMapper : inferenceMapper
            argType = checkExpressionWithContextualType(arg, paramType, mapper)
          }

          inferTypes(context, argType, paramType)
        }
      }

      // In the second pass we visit only context sensitive arguments, and only those that aren't excluded, this
      // time treating def expressions normally (which may cause previously inferred type arguments to be fixed
      // as we construct types for contextually typed parameters)
      // Decorators will not have `excludeArgument`, as their arguments cannot be contextually typed.
      // Tagged template expressions will always have `()` for `excludeArgument[0]`.
      if (excludeArgument) {
        for (var i = 0; i < argCount; i++) {
          // No need to check for omitted args and template expressions, their exclusion value is always ()
          if (excludeArgument[i] == false) {
            val arg = args[i]
            val paramType = getTypeAtPosition(signature, i)
            inferTypes(context, checkExpressionWithContextualType(arg, paramType, inferenceMapper), paramType)
          }
        }
      }

      getInferredTypes(context)
    }

    def checkTypeArguments(signature: Signature, typeArgumentNodes: TypeNode[], typeArgumentTypes: Type[], reportErrors: Boolean, headMessage?: DiagnosticMessage): Boolean {
      val typeParameters = signature.typeParameters
      var typeArgumentsAreAssignable = true
      var mapper: TypeMapper
      for (var i = 0; i < typeParameters.length; i++) {
        if (typeArgumentsAreAssignable /* so far */) {
          val constraint = getConstraintOfTypeParameter(typeParameters[i])
          if (constraint) {
            var errorInfo: DiagnosticMessageChain
            var typeArgumentHeadMessage = Diagnostics.Type_0_does_not_satisfy_the_constraint_1
            if (reportErrors && headMessage) {
              errorInfo = chainDiagnosticMessages(errorInfo, typeArgumentHeadMessage)
              typeArgumentHeadMessage = headMessage
            }
            if (!mapper) {
              mapper = createTypeMapper(typeParameters, typeArgumentTypes)
            }
            val typeArgument = typeArgumentTypes[i]
            typeArgumentsAreAssignable = checkTypeAssignableTo(
              typeArgument,
              getTypeWithThisArgument(instantiateType(constraint, mapper), typeArgument),
              reportErrors ? typeArgumentNodes[i] : (),
              typeArgumentHeadMessage,
              errorInfo)
          }
        }
      }
      return typeArgumentsAreAssignable
    }

    def checkApplicableSignature(node: CallLikeExpression, args: Expression[], signature: Signature, relation: Map<RelationComparisonResult>, excludeArgument: Boolean[], reportErrors: Boolean) {
      val argCount = getEffectiveArgumentCount(node, args, signature)
      for (var i = 0; i < argCount; i++) {
        val arg = getEffectiveArgument(node, args, i)
        // If the effective argument is '()', then it is an argument that is present but is synthetic.
        if (arg == () || arg.kind != SyntaxKind.OmittedExpression) {
          // Check spread elements against rest type (from arity check we know spread argument corresponds to a rest parameter)
          val paramType = getTypeAtPosition(signature, i)
          var argType = getEffectiveArgumentType(node, i, arg)

          // If the effective argument type is '()', there is no synthetic type
          // for the argument. In that case, we should check the argument.
          if (argType == ()) {
            argType = arg.kind == SyntaxKind.StringLiteral && !reportErrors
              ? getStringLiteralTypeForText((<StringLiteral>arg).text)
              : checkExpressionWithContextualType(arg, paramType, excludeArgument && excludeArgument[i] ? identityMapper : ())
          }

          // Use argument expression as error location when reporting errors
          val errorNode = reportErrors ? getEffectiveArgumentErrorNode(node, i, arg) : ()
          val headMessage = Diagnostics.Argument_of_type_0_is_not_assignable_to_parameter_of_type_1
          if (!checkTypeRelatedTo(argType, paramType, relation, errorNode, headMessage)) {
            return false
          }
        }
      }

      return true
    }

    /**
     * Returns the effective arguments for an expression that works like a def invocation.
     *
     * If 'node' is a CallExpression or a NewExpression, then its argument list is returned.
     * If 'node' is a TaggedTemplateExpression, a new argument list is constructed from the substitution
     *  expressions, where the first element of the list is `()`.
     * If 'node' is a Decorator, the argument list will be `()`, and its arguments and types
     *  will be supplied from calls to `getEffectiveArgumentCount` and `getEffectiveArgumentType`.
     */
    def getEffectiveCallArguments(node: CallLikeExpression): Expression[] {
      var args: Expression[]
      if (node.kind == SyntaxKind.TaggedTemplateExpression) {
        val template = (<TaggedTemplateExpression>node).template
        args = [()]
        if (template.kind == SyntaxKind.TemplateExpression) {
          forEach((<TemplateExpression>template).templateSpans, span => {
            args.push(span.expression)
          })
        }
      }
      else if (node.kind == SyntaxKind.Decorator) {
        // For a decorator, we return () as we will determine
        // the Int and types of arguments for a decorator using
        // `getEffectiveArgumentCount` and `getEffectiveArgumentType` below.
        return ()
      }
      else {
        args = (<CallExpression>node).arguments || emptyArray
      }

      return args
    }


    /**
      * Returns the effective argument count for a node that works like a def invocation.
      * If 'node' is a Decorator, the Int of arguments is derived from the decoration
      *  target and the signature:
      *  If 'node.target' is a class declaration or class expression, the effective argument
      *     count is 1.
      *  If 'node.target' is a parameter declaration, the effective argument count is 3.
      *  If 'node.target' is a property declaration, the effective argument count is 2.
      *  If 'node.target' is a method or accessor declaration, the effective argument count
      *     is 3, although it can be 2 if the signature only accepts two arguments, allowing
      *     us to match a property decorator.
      * Otherwise, the argument count is the length of the 'args' array.
      */
    def getEffectiveArgumentCount(node: CallLikeExpression, args: Expression[], signature: Signature) {
      if (node.kind == SyntaxKind.Decorator) {
        switch (node.parent.kind) {
          case SyntaxKind.ClassDeclaration:
          case SyntaxKind.ClassExpression:
            // A class decorator will have one argument (see `ClassDecorator` in core.d.ts)
            return 1

          case SyntaxKind.PropertyDeclaration:
            // A property declaration decorator will have two arguments (see
            // `PropertyDecorator` in core.d.ts)
            return 2

          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.GetAccessor:
          case SyntaxKind.SetAccessor:
            // A method or accessor declaration decorator will have two or three arguments (see
            // `PropertyDecorator` and `MethodDecorator` in core.d.ts)

            // If we are emitting decorators for ES3, we will only pass two arguments.
            if (languageVersion == ScriptTarget.ES3) {
              return 2
            }

            // If the method decorator signature only accepts a target and a key, we will only
            // type check those arguments.
            return signature.parameters.length >= 3 ? 3 : 2

          case SyntaxKind.Parameter:
            // A parameter declaration decorator will have three arguments (see
            // `ParameterDecorator` in core.d.ts)

            return 3
        }
      }
      else {
        return args.length
      }
    }

    /**
      * Returns the effective type of the first argument to a decorator.
      * If 'node' is a class declaration or class expression, the effective argument type
      *  is the type of the static side of the class.
      * If 'node' is a parameter declaration, the effective argument type is either the type
      *  of the static or instance side of the class for the parameter's parent method,
      *  depending on whether the method is declared static.
      *  For a constructor, the type is always the type of the static side of the class.
      * If 'node' is a property, method, or accessor declaration, the effective argument
      *  type is the type of the static or instance side of the parent class for class
      *  element, depending on whether the element is declared static.
      */
    def getEffectiveDecoratorFirstArgumentType(node: Node): Type {
      // The first argument to a decorator is its `target`.
      if (node.kind == SyntaxKind.ClassDeclaration) {
        // For a class decorator, the `target` is the type of the class (e.g. the
        // "static" or "constructor" side of the class)
        val classSymbol = getSymbolOfNode(node)
        return getTypeOfSymbol(classSymbol)
      }

      if (node.kind == SyntaxKind.Parameter) {
        // For a parameter decorator, the `target` is the parent type of the
        // parameter's containing method.
        node = node.parent
        if (node.kind == SyntaxKind.Constructor) {
          val classSymbol = getSymbolOfNode(node)
          return getTypeOfSymbol(classSymbol)
        }
      }

      if (node.kind == SyntaxKind.PropertyDeclaration ||
        node.kind == SyntaxKind.MethodDeclaration ||
        node.kind == SyntaxKind.GetAccessor ||
        node.kind == SyntaxKind.SetAccessor) {
        // For a property or method decorator, the `target` is the
        // "static"-side type of the parent of the member if the member is
        // declared "static"; otherwise, it is the "instance"-side type of the
        // parent of the member.
        return getParentTypeOfClassElement(<ClassElement>node)
      }

      Debug.fail("Unsupported decorator target.")
      return unknownType
    }

    /**
      * Returns the effective type for the second argument to a decorator.
      * If 'node' is a parameter, its effective argument type is one of the following:
      *  If 'node.parent' is a constructor, the effective argument type is 'any', as we
      *     will emit `()`.
      *  If 'node.parent' is a member with an identifier, numeric, or String literal name,
      *     the effective argument type will be a String literal type for the member name.
      *  If 'node.parent' is a computed property name, the effective argument type will
      *     either be a symbol type or the String type.
      * If 'node' is a member with an identifier, numeric, or String literal name, the
      *  effective argument type will be a String literal type for the member name.
      * If 'node' is a computed property name, the effective argument type will either
      *  be a symbol type or the String type.
      * A class decorator does not have a second argument type.
      */
    def getEffectiveDecoratorSecondArgumentType(node: Node) {
      // The second argument to a decorator is its `propertyKey`
      if (node.kind == SyntaxKind.ClassDeclaration) {
        Debug.fail("Class decorators should not have a second synthetic argument.")
        return unknownType
      }

      if (node.kind == SyntaxKind.Parameter) {
        node = node.parent
        if (node.kind == SyntaxKind.Constructor) {
          // For a constructor parameter decorator, the `propertyKey` will be `()`.
          return anyType
        }

        // For a non-constructor parameter decorator, the `propertyKey` will be either
        // a String or a symbol, based on the name of the parameter's containing method.
      }

      if (node.kind == SyntaxKind.PropertyDeclaration ||
        node.kind == SyntaxKind.MethodDeclaration ||
        node.kind == SyntaxKind.GetAccessor ||
        node.kind == SyntaxKind.SetAccessor) {
        // The `propertyKey` for a property or method decorator will be a
        // String literal type if the member name is an identifier, Int, or String
        // otherwise, if the member name is a computed property name it will
        // be either String or symbol.
        val element = <ClassElement>node
        switch (element.name.kind) {
          case SyntaxKind.Identifier:
          case SyntaxKind.NumericLiteral:
          case SyntaxKind.StringLiteral:
            return getStringLiteralTypeForText((<Identifier | LiteralExpression>element.name).text)

          case SyntaxKind.ComputedPropertyName:
            val nameType = checkComputedPropertyName(<ComputedPropertyName>element.name)
            if (isTypeOfKind(nameType, TypeFlags.ESSymbol)) {
              return nameType
            }
            else {
              return stringType
            }

          default:
            Debug.fail("Unsupported property name.")
            return unknownType
        }
      }

      Debug.fail("Unsupported decorator target.")
      return unknownType
    }

    /**
      * Returns the effective argument type for the third argument to a decorator.
      * If 'node' is a parameter, the effective argument type is the Int type.
      * If 'node' is a method or accessor, the effective argument type is a
      *  `TypedPropertyDescriptor<T>` instantiated with the type of the member.
      * Class and property decorators do not have a third effective argument.
      */
    def getEffectiveDecoratorThirdArgumentType(node: Node) {
      // The third argument to a decorator is either its `descriptor` for a method decorator
      // or its `parameterIndex` for a parameter decorator
      if (node.kind == SyntaxKind.ClassDeclaration) {
        Debug.fail("Class decorators should not have a third synthetic argument.")
        return unknownType
      }

      if (node.kind == SyntaxKind.Parameter) {
        // The `parameterIndex` for a parameter decorator is always a Int
        return numberType
      }

      if (node.kind == SyntaxKind.PropertyDeclaration) {
        Debug.fail("Property decorators should not have a third synthetic argument.")
        return unknownType
      }

      if (node.kind == SyntaxKind.MethodDeclaration ||
        node.kind == SyntaxKind.GetAccessor ||
        node.kind == SyntaxKind.SetAccessor) {
        // The `descriptor` for a method decorator will be a `TypedPropertyDescriptor<T>`
        // for the type of the member.
        val propertyType = getTypeOfNode(node)
        return createTypedPropertyDescriptorType(propertyType)
      }

      Debug.fail("Unsupported decorator target.")
      return unknownType
    }

    /**
      * Returns the effective argument type for the provided argument to a decorator.
      */
    def getEffectiveDecoratorArgumentType(node: Decorator, argIndex: Int): Type {
      if (argIndex == 0) {
        return getEffectiveDecoratorFirstArgumentType(node.parent)
      }
      else if (argIndex == 1) {
        return getEffectiveDecoratorSecondArgumentType(node.parent)
      }
      else if (argIndex == 2) {
        return getEffectiveDecoratorThirdArgumentType(node.parent)
      }

      Debug.fail("Decorators should not have a fourth synthetic argument.")
      return unknownType
    }

    /**
      * Gets the effective argument type for an argument in a call expression.
      */
    def getEffectiveArgumentType(node: CallLikeExpression, argIndex: Int, arg: Expression): Type {
      // Decorators provide special arguments, a tagged template expression provides
      // a special first argument, and String literals get String literal types
      // unless we're reporting errors
      if (node.kind == SyntaxKind.Decorator) {
        return getEffectiveDecoratorArgumentType(<Decorator>node, argIndex)
      }
      else if (argIndex == 0 && node.kind == SyntaxKind.TaggedTemplateExpression) {
        return globalTemplateStringsArrayType
      }

      // This is not a synthetic argument, so we return '()'
      // to signal that the caller needs to check the argument.
      return ()
    }

    /**
      * Gets the effective argument expression for an argument in a call expression.
      */
    def getEffectiveArgument(node: CallLikeExpression, args: Expression[], argIndex: Int) {
      // For a decorator or the first argument of a tagged template expression we return ().
      if (node.kind == SyntaxKind.Decorator ||
        (argIndex == 0 && node.kind == SyntaxKind.TaggedTemplateExpression)) {
        return ()
      }

      return args[argIndex]
    }

    /**
      * Gets the error node to use when reporting errors for an effective argument.
      */
    def getEffectiveArgumentErrorNode(node: CallLikeExpression, argIndex: Int, arg: Expression) {
      if (node.kind == SyntaxKind.Decorator) {
        // For a decorator, we use the expression of the decorator for error reporting.
        return (<Decorator>node).expression
      }
      else if (argIndex == 0 && node.kind == SyntaxKind.TaggedTemplateExpression) {
        // For a the first argument of a tagged template expression, we use the template of the tag for error reporting.
        return (<TaggedTemplateExpression>node).template
      }
      else {
        return arg
      }
    }

    def resolveCall(node: CallLikeExpression, signatures: Signature[], candidatesOutArray: Signature[], headMessage?: DiagnosticMessage): Signature {
      val isTaggedTemplate = node.kind == SyntaxKind.TaggedTemplateExpression
      val isDecorator = node.kind == SyntaxKind.Decorator

      var typeArguments: TypeNode[]

      if (!isTaggedTemplate && !isDecorator) {
        typeArguments = (<CallExpression>node).typeArguments

        // We already perform checking on the type arguments on the class declaration itself.
        if ((<CallExpression>node).expression.kind != SyntaxKind.SuperKeyword) {
          forEach(typeArguments, checkSourceElement)
        }
      }

      val candidates = candidatesOutArray || []
      // reorderCandidates fills up the candidates array directly
      reorderCandidates(signatures, candidates)
      if (!candidates.length) {
        reportError(Diagnostics.Supplied_parameters_do_not_match_any_signature_of_call_target)
        return resolveErrorCall(node)
      }

      val args = getEffectiveCallArguments(node)

      // The following applies to any value of 'excludeArgument[i]':
      //  - true:    the argument at 'i' is susceptible to a one-time permanent contextual typing.
      //  - (): the argument at 'i' is *not* susceptible to permanent contextual typing.
      //  - false:   the argument at 'i' *was* and *has been* permanently contextually typed.
      //
      // The idea is that we will perform type argument inference & assignability checking once
      // without using the susceptible parameters that are functions, and once more for each of those
      // parameters, contextually typing each as we go along.
      //
      // For a tagged template, then the first argument be '()' if necessary
      // because it represents a TemplateStringsArray.
      //
      // For a decorator, no arguments are susceptible to contextual typing due to the fact
      // decorators are applied to a declaration by the emitter, and not to an expression.
      var excludeArgument: Boolean[]
      if (!isDecorator) {
        // We do not need to call `getEffectiveArgumentCount` here as it only
        // applies when calculating the Int of arguments for a decorator.
        for (var i = isTaggedTemplate ? 1 : 0; i < args.length; i++) {
          if (isContextSensitive(args[i])) {
            if (!excludeArgument) {
              excludeArgument = new Array(args.length)
            }
            excludeArgument[i] = true
          }
        }
      }

      // The following variables are captured and modified by calls to chooseOverload.
      // If overload resolution or type argument inference fails, we want to report the
      // best error possible. The best error is one which says that an argument was not
      // assignable to a parameter. This implies that everything else about the overload
      // was fine. So if there is any overload that is only incorrect because of an
      // argument, we will report an error on that one.
      //
      //   def foo(s: String) {}
      //   def foo(n: Int) {} // Report argument error on this overload
      //   def foo() {}
      //   foo(true)
      //
      // If none of the overloads even made it that far, there are two possibilities.
      // There was a problem with type arguments for some overload, in which case
      // report an error on that. Or none of the overloads even had correct arity,
      // in which case give an arity error.
      //
      //   def foo<T>(x: T, y: T) {} // Report type argument inference error
      //   def foo() {}
      //   foo(0, true)
      //
      var candidateForArgumentError: Signature
      var candidateForTypeArgumentError: Signature
      var resultOfFailedInference: InferenceContext
      var result: Signature

      // Section 4.12.1:
      // if the candidate list contains one or more signatures for which the type of each argument
      // expression is a subtype of each corresponding parameter type, the return type of the first
      // of those signatures becomes the return type of the def call.
      // Otherwise, the return type of the first signature in the candidate list becomes the return
      // type of the def call.
      //
      // Whether the call is an error is determined by assignability of the arguments. The subtype pass
      // is just important for choosing the best signature. So in the case where there is only one
      // signature, the subtype pass is useless. So skipping it is an optimization.
      if (candidates.length > 1) {
        result = chooseOverload(candidates, subtypeRelation)
      }
      if (!result) {
        // Reinitialize these pointers for round two
        candidateForArgumentError = ()
        candidateForTypeArgumentError = ()
        resultOfFailedInference = ()
        result = chooseOverload(candidates, assignableRelation)
      }
      if (result) {
        return result
      }

      // No signatures were applicable. Now report errors based on the last applicable signature with
      // no arguments excluded from assignability checks.
      // If candidate is (), it means that no candidates had a suitable arity. In that case,
      // skip the checkApplicableSignature check.
      if (candidateForArgumentError) {
        // excludeArgument is (), in this case also equivalent to [(), (), ...]
        // The importance of excludeArgument is to prevent us from typing def expression parameters
        // in arguments too early. If possible, we'd like to only type them once we know the correct
        // overload. However, this matters for the case where the call is correct. When the call is
        // an error, we don't need to exclude any arguments, although it would cause no harm to do so.
        checkApplicableSignature(node, args, candidateForArgumentError, assignableRelation, /*excludeArgument*/ (), /*reportErrors*/ true)
      }
      else if (candidateForTypeArgumentError) {
        if (!isTaggedTemplate && !isDecorator && typeArguments) {
          val typeArguments = (<CallExpression>node).typeArguments
          checkTypeArguments(candidateForTypeArgumentError, typeArguments, map(typeArguments, getTypeFromTypeNode), /*reportErrors*/ true, headMessage)
        }
        else {
          Debug.assert(resultOfFailedInference.failedTypeParameterIndex >= 0)
          val failedTypeParameter = candidateForTypeArgumentError.typeParameters[resultOfFailedInference.failedTypeParameterIndex]
          val inferenceCandidates = getInferenceCandidates(resultOfFailedInference, resultOfFailedInference.failedTypeParameterIndex)

          var diagnosticChainHead = chainDiagnosticMessages(/*details*/ (), // details will be provided by call to reportNoCommonSupertypeError
            Diagnostics.The_type_argument_for_type_parameter_0_cannot_be_inferred_from_the_usage_Consider_specifying_the_type_arguments_explicitly,
            typeToString(failedTypeParameter))

          if (headMessage) {
            diagnosticChainHead = chainDiagnosticMessages(diagnosticChainHead, headMessage)
          }

          reportNoCommonSupertypeError(inferenceCandidates, (<CallExpression>node).expression || (<TaggedTemplateExpression>node).tag, diagnosticChainHead)
        }
      }
      else {
        reportError(Diagnostics.Supplied_parameters_do_not_match_any_signature_of_call_target)
      }

      // No signature was applicable. We have already reported the errors for the invalid signature.
      // If this is a type resolution session, e.g. Language Service, try to get better information that anySignature.
      // Pick the first candidate that matches the arity. This way we can get a contextual type for cases like:
      //  declare def f(a: { xa: Int; xb: Int; })
      //  f({ |
      if (!produceDiagnostics) {
        for (var candidate of candidates) {
          if (hasCorrectArity(node, args, candidate)) {
            if (candidate.typeParameters && typeArguments) {
              candidate = getSignatureInstantiation(candidate, map(typeArguments, getTypeFromTypeNode))
            }
            return candidate
          }
        }
      }

      return resolveErrorCall(node)

      def reportError(message: DiagnosticMessage, arg0?: String, arg1?: String, arg2?: String): Unit {
        var errorInfo: DiagnosticMessageChain
        errorInfo = chainDiagnosticMessages(errorInfo, message, arg0, arg1, arg2)
        if (headMessage) {
          errorInfo = chainDiagnosticMessages(errorInfo, headMessage)
        }

        diagnostics.add(createDiagnosticForNodeFromMessageChain(node, errorInfo))
      }

      def chooseOverload(candidates: Signature[], relation: Map<RelationComparisonResult>) {
        for (val originalCandidate of candidates) {
          if (!hasCorrectArity(node, args, originalCandidate)) {
            continue
          }

          var candidate: Signature
          var typeArgumentsAreValid: Boolean
          val inferenceContext = originalCandidate.typeParameters
            ? createInferenceContext(originalCandidate.typeParameters, /*inferUnionTypes*/ false)
            : ()

          while (true) {
            candidate = originalCandidate
            if (candidate.typeParameters) {
              var typeArgumentTypes: Type[]
              if (typeArguments) {
                typeArgumentTypes = map(typeArguments, getTypeFromTypeNode)
                typeArgumentsAreValid = checkTypeArguments(candidate, typeArguments, typeArgumentTypes, /*reportErrors*/ false)
              }
              else {
                inferTypeArguments(node, candidate, args, excludeArgument, inferenceContext)
                typeArgumentsAreValid = inferenceContext.failedTypeParameterIndex == ()
                typeArgumentTypes = inferenceContext.inferredTypes
              }
              if (!typeArgumentsAreValid) {
                break
              }
              candidate = getSignatureInstantiation(candidate, typeArgumentTypes)
            }
            if (!checkApplicableSignature(node, args, candidate, relation, excludeArgument, /*reportErrors*/ false)) {
              break
            }
            val index = excludeArgument ? indexOf(excludeArgument, true) : -1
            if (index < 0) {
              return candidate
            }
            excludeArgument[index] = false
          }

          // A post-mortem of this iteration of the loop. The signature was not applicable,
          // so we want to track it as a candidate for reporting an error. If the candidate
          // had no type parameters, or had no issues related to type arguments, we can
          // report an error based on the arguments. If there was an issue with type
          // arguments, then we can only report an error based on the type arguments.
          if (originalCandidate.typeParameters) {
            val instantiatedCandidate = candidate
            if (typeArgumentsAreValid) {
              candidateForArgumentError = instantiatedCandidate
            }
            else {
              candidateForTypeArgumentError = originalCandidate
              if (!typeArguments) {
                resultOfFailedInference = inferenceContext
              }
            }
          }
          else {
            Debug.assert(originalCandidate == candidate)
            candidateForArgumentError = originalCandidate
          }
        }

        return ()
      }

    }

    def resolveCallExpression(node: CallExpression, candidatesOutArray: Signature[]): Signature {
      if (node.expression.kind == SyntaxKind.SuperKeyword) {
        val superType = checkSuperExpression(node.expression)
        if (superType != unknownType) {
          // In super call, the candidate signatures are the matching arity signatures of the base constructor def instantiated
          // with the type arguments specified in the extends clause.
          val baseTypeNode = getClassExtendsHeritageClauseElement(getContainingClass(node))
          val baseConstructors = getInstantiatedConstructorsForTypeArguments(superType, baseTypeNode.typeArguments)
          return resolveCall(node, baseConstructors, candidatesOutArray)
        }
        return resolveUntypedCall(node)
      }

      val funcType = checkExpression(node.expression)
      val apparentType = getApparentType(funcType)

      if (apparentType == unknownType) {
        // Another error has already been reported
        return resolveErrorCall(node)
      }

      // Technically, this signatures list may be incomplete. We are taking the apparent type,
      // but we are not including call signatures that may have been added to the Object or
      // Function trait, since they have none by default. This is a bit of a leap of faith
      // that the user will not add any.
      val callSignatures = getSignaturesOfType(apparentType, SignatureKind.Call)

      val constructSignatures = getSignaturesOfType(apparentType, SignatureKind.Construct)
      // TS 1.0 spec: 4.12
      // If FuncExpr is of type Any, or of an object type that has no call or construct signatures
      // but is a subtype of the Function trait, the call is an untyped def call. In an
      // untyped def call no TypeArgs are permitted, Args can be any argument list, no contextual
      // types are provided for the argument expressions, and the result is always of type Any.
      // We exclude union types because we may have a union of def types that happen to have
      // no common signatures.
      if (isTypeAny(funcType) || (!callSignatures.length && !constructSignatures.length && !(funcType.flags & TypeFlags.Union) && isTypeAssignableTo(funcType, globalFunctionType))) {
        // The unknownType indicates that an error already occurred (and was reported).  No
        // need to report another error in this case.
        if (funcType != unknownType && node.typeArguments) {
          error(node, Diagnostics.Untyped_function_calls_may_not_accept_type_arguments)
        }
        return resolveUntypedCall(node)
      }
      // If FuncExpr's apparent type(section 3.8.1) is a def type, the call is a typed def call.
      // TypeScript employs overload resolution in typed def calls in order to support functions
      // with multiple call signatures.
      if (!callSignatures.length) {
        if (constructSignatures.length) {
          error(node, Diagnostics.Value_of_type_0_is_not_callable_Did_you_mean_to_include_new, typeToString(funcType))
        }
        else {
          error(node, Diagnostics.Cannot_invoke_an_expression_whose_type_lacks_a_call_signature)
        }
        return resolveErrorCall(node)
      }
      return resolveCall(node, callSignatures, candidatesOutArray)
    }

    def resolveNewExpression(node: NewExpression, candidatesOutArray: Signature[]): Signature {
      if (node.arguments && languageVersion < ScriptTarget.ES5) {
        val spreadIndex = getSpreadArgumentIndex(node.arguments)
        if (spreadIndex >= 0) {
          error(node.arguments[spreadIndex], Diagnostics.Spread_operator_in_new_expressions_is_only_available_when_targeting_ECMAScript_5_and_higher)
        }
      }

      var expressionType = checkExpression(node.expression)

      // If expressionType's apparent type(section 3.8.1) is an object type with one or
      // more construct signatures, the expression is processed in the same manner as a
      // def call, but using the construct signatures as the initial set of candidate
      // signatures for overload resolution. The result type of the def call becomes
      // the result type of the operation.
      expressionType = getApparentType(expressionType)
      if (expressionType == unknownType) {
        // Another error has already been reported
        return resolveErrorCall(node)
      }

      // If the expression is a class of abstract type, then it cannot be instantiated.
      // Note, only class declarations can be declared abstract.
      // In the case of a merged class-module or class-trait declaration,
      // only the class declaration node will have the Abstract flag set.
      val valueDecl = expressionType.symbol && getClassLikeDeclarationOfSymbol(expressionType.symbol)
      if (valueDecl && valueDecl.flags & NodeFlags.Abstract) {
        error(node, Diagnostics.Cannot_create_an_instance_of_the_abstract_class_0, declarationNameToString(valueDecl.name))
        return resolveErrorCall(node)
      }

      // TS 1.0 spec: 4.11
      // If expressionType is of type Any, Args can be any argument
      // list and the result of the operation is of type Any.
      if (isTypeAny(expressionType)) {
        if (node.typeArguments) {
          error(node, Diagnostics.Untyped_function_calls_may_not_accept_type_arguments)
        }
        return resolveUntypedCall(node)
      }

      // Technically, this signatures list may be incomplete. We are taking the apparent type,
      // but we are not including construct signatures that may have been added to the Object or
      // Function trait, since they have none by default. This is a bit of a leap of faith
      // that the user will not add any.
      val constructSignatures = getSignaturesOfType(expressionType, SignatureKind.Construct)
      if (constructSignatures.length) {
        return resolveCall(node, constructSignatures, candidatesOutArray)
      }

      // If expressionType's apparent type is an object type with no construct signatures but
      // one or more call signatures, the expression is processed as a def call. A compile-time
      // error occurs if the result of the def call is not Void. The type of the result of the
      // operation is Any.
      val callSignatures = getSignaturesOfType(expressionType, SignatureKind.Call)
      if (callSignatures.length) {
        val signature = resolveCall(node, callSignatures, candidatesOutArray)
        if (getReturnTypeOfSignature(signature) != voidType) {
          error(node, Diagnostics.Only_a_void_function_can_be_called_with_the_new_keyword)
        }
        return signature
      }

      error(node, Diagnostics.Cannot_use_new_with_an_expression_whose_type_lacks_a_call_or_construct_signature)
      return resolveErrorCall(node)
    }

    def resolveTaggedTemplateExpression(node: TaggedTemplateExpression, candidatesOutArray: Signature[]): Signature {
      val tagType = checkExpression(node.tag)
      val apparentType = getApparentType(tagType)

      if (apparentType == unknownType) {
        // Another error has already been reported
        return resolveErrorCall(node)
      }

      val callSignatures = getSignaturesOfType(apparentType, SignatureKind.Call)

      if (isTypeAny(tagType) || (!callSignatures.length && !(tagType.flags & TypeFlags.Union) && isTypeAssignableTo(tagType, globalFunctionType))) {
        return resolveUntypedCall(node)
      }

      if (!callSignatures.length) {
        error(node, Diagnostics.Cannot_invoke_an_expression_whose_type_lacks_a_call_signature)
        return resolveErrorCall(node)
      }

      return resolveCall(node, callSignatures, candidatesOutArray)
    }

    /**
      * Gets the localized diagnostic head message to use for errors when resolving a decorator as a call expression.
      */
    def getDiagnosticHeadMessageForDecoratorResolution(node: Decorator) {
      switch (node.parent.kind) {
        case SyntaxKind.ClassDeclaration:
        case SyntaxKind.ClassExpression:
          return Diagnostics.Unable_to_resolve_signature_of_class_decorator_when_called_as_an_expression

        case SyntaxKind.Parameter:
          return Diagnostics.Unable_to_resolve_signature_of_parameter_decorator_when_called_as_an_expression

        case SyntaxKind.PropertyDeclaration:
          return Diagnostics.Unable_to_resolve_signature_of_property_decorator_when_called_as_an_expression

        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.GetAccessor:
        case SyntaxKind.SetAccessor:
          return Diagnostics.Unable_to_resolve_signature_of_method_decorator_when_called_as_an_expression
      }
    }

    /**
      * Resolves a decorator as if it were a call expression.
      */
    def resolveDecorator(node: Decorator, candidatesOutArray: Signature[]): Signature {
      val funcType = checkExpression(node.expression)
      val apparentType = getApparentType(funcType)
      if (apparentType == unknownType) {
        return resolveErrorCall(node)
      }

      val callSignatures = getSignaturesOfType(apparentType, SignatureKind.Call)
      if (funcType == anyType || (!callSignatures.length && !(funcType.flags & TypeFlags.Union) && isTypeAssignableTo(funcType, globalFunctionType))) {
        return resolveUntypedCall(node)
      }

      val headMessage = getDiagnosticHeadMessageForDecoratorResolution(node)
      if (!callSignatures.length) {
        var errorInfo: DiagnosticMessageChain
        errorInfo = chainDiagnosticMessages(errorInfo, Diagnostics.Cannot_invoke_an_expression_whose_type_lacks_a_call_signature)
        errorInfo = chainDiagnosticMessages(errorInfo, headMessage)
        diagnostics.add(createDiagnosticForNodeFromMessageChain(node, errorInfo))
        return resolveErrorCall(node)
      }

      return resolveCall(node, callSignatures, candidatesOutArray, headMessage)
    }

    // candidatesOutArray is passed by signature help in the language service, and collectCandidates
    // must fill it up with the appropriate candidate signatures
    def getResolvedSignature(node: CallLikeExpression, candidatesOutArray?: Signature[]): Signature {
      val links = getNodeLinks(node)
      // If getResolvedSignature has already been called, we will have cached the resolvedSignature.
      // However, it is possible that either candidatesOutArray was not passed in the first time,
      // or that a different candidatesOutArray was passed in. Therefore, we need to redo the work
      // to correctly fill the candidatesOutArray.
      if (!links.resolvedSignature || candidatesOutArray) {
        links.resolvedSignature = anySignature

        if (node.kind == SyntaxKind.CallExpression) {
          links.resolvedSignature = resolveCallExpression(<CallExpression>node, candidatesOutArray)
        }
        else if (node.kind == SyntaxKind.NewExpression) {
          links.resolvedSignature = resolveNewExpression(<NewExpression>node, candidatesOutArray)
        }
        else if (node.kind == SyntaxKind.TaggedTemplateExpression) {
          links.resolvedSignature = resolveTaggedTemplateExpression(<TaggedTemplateExpression>node, candidatesOutArray)
        }
        else if (node.kind == SyntaxKind.Decorator) {
          links.resolvedSignature = resolveDecorator(<Decorator>node, candidatesOutArray)
        }
        else {
          Debug.fail("Branch in 'getResolvedSignature' should be unreachable.")
        }
      }
      return links.resolvedSignature
    }

    def getInferredClassType(symbol: Symbol) {
      val links = getSymbolLinks(symbol)
      if (!links.inferredClassType) {
        links.inferredClassType = createAnonymousType((), symbol.members, emptyArray, emptyArray, /*stringIndexType*/ (), /*numberIndexType*/ ())
      }
      return links.inferredClassType
    }

    /**
     * Syntactically and semantically checks a call or new expression.
     * @param node The call/new expression to be checked.
     * @returns On success, the expression's signature's return type. On failure, anyType.
     */
    def checkCallExpression(node: CallExpression): Type {
      // Grammar checking; stop grammar-checking if checkGrammarTypeArguments return true
      checkGrammarTypeArguments(node, node.typeArguments) || checkGrammarArguments(node, node.arguments)

      val signature = getResolvedSignature(node)
      if (node.expression.kind == SyntaxKind.SuperKeyword) {
        val containingFunction = getContainingFunction(node.expression)

        if (containingFunction && containingFunction.kind == SyntaxKind.Constructor) {
          getNodeLinks(containingFunction).flags |= NodeCheckFlags.HasSeenSuperCall
        }
        return voidType
      }
      if (node.kind == SyntaxKind.NewExpression) {
        val declaration = signature.declaration

        if (declaration &&
          declaration.kind != SyntaxKind.Constructor &&
          declaration.kind != SyntaxKind.ConstructSignature &&
          declaration.kind != SyntaxKind.ConstructorType &&
          !isJSDocConstructSignature(declaration)) {

          // When resolved signature is a call signature (and not a construct signature) the result type is any, unless
          // the declaring def had members created through 'x.prototype.y = expr' or 'this.y = expr' psuedodeclarations
          // in a JS file
          val funcSymbol = checkExpression(node.expression).symbol
          if (funcSymbol && funcSymbol.members && (funcSymbol.flags & SymbolFlags.Function)) {
            return getInferredClassType(funcSymbol)
          }
          else if (compilerOptions.noImplicitAny) {
            error(node, Diagnostics.new_expression_whose_target_lacks_a_construct_signature_implicitly_has_an_any_type)
          }
          return anyType
        }
      }

      // In JavaScript files, calls to any identifier 'require' are treated as external module imports
      if (isInJavaScriptFile(node) && isRequireCall(node, /*checkArgumentIsStringLiteral*/true)) {
        return resolveExternalModuleTypeByLiteral(<StringLiteral>node.arguments[0])
      }

      return getReturnTypeOfSignature(signature)
    }

    def checkTaggedTemplateExpression(node: TaggedTemplateExpression): Type {
      return getReturnTypeOfSignature(getResolvedSignature(node))
    }

    def checkAssertion(node: AssertionExpression) {
      val exprType = getRegularTypeOfObjectLiteral(checkExpression(node.expression))
      val targetType = getTypeFromTypeNode(node.type)
      if (produceDiagnostics && targetType != unknownType) {
        val widenedType = getWidenedType(exprType)

        // Permit 'Int[] | "foo"' to be asserted to 'String'.
        val bothAreStringLike = maybeTypeOfKind(targetType, TypeFlags.StringLike) &&
          maybeTypeOfKind(widenedType, TypeFlags.StringLike)
        if (!bothAreStringLike && !(isTypeAssignableTo(targetType, widenedType))) {
          checkTypeAssignableTo(exprType, targetType, node, Diagnostics.Neither_type_0_nor_type_1_is_assignable_to_the_other)
        }
      }
      return targetType
    }

    def getTypeAtPosition(signature: Signature, pos: Int): Type {
      return signature.hasRestParameter ?
        pos < signature.parameters.length - 1 ? getTypeOfSymbol(signature.parameters[pos]) : getRestTypeOfSignature(signature) :
        pos < signature.parameters.length ? getTypeOfSymbol(signature.parameters[pos]) : anyType
    }

    def assignContextualParameterTypes(signature: Signature, context: Signature, mapper: TypeMapper) {
      val len = signature.parameters.length - (signature.hasRestParameter ? 1 : 0)
      for (var i = 0; i < len; i++) {
        val parameter = signature.parameters[i]
        val contextualParameterType = getTypeAtPosition(context, i)
        assignTypeToParameterAndFixTypeParameters(parameter, contextualParameterType, mapper)
      }
      if (signature.hasRestParameter && isRestParameterIndex(context, signature.parameters.length - 1)) {
        val parameter = lastOrUndefined(signature.parameters)
        val contextualParameterType = getTypeOfSymbol(lastOrUndefined(context.parameters))
        assignTypeToParameterAndFixTypeParameters(parameter, contextualParameterType, mapper)
      }
    }

    // When contextual typing assigns a type to a parameter that contains a binding pattern, we also need to push
    // the destructured type into the contained binding elements.
    def assignBindingElementTypes(node: VariableLikeDeclaration) {
      if (isBindingPattern(node.name)) {
        for (val element of (<BindingPattern>node.name).elements) {
          if (element.kind != SyntaxKind.OmittedExpression) {
            if (element.name.kind == SyntaxKind.Identifier) {
              getSymbolLinks(getSymbolOfNode(element)).type = getTypeForBindingElement(element)
            }
            assignBindingElementTypes(element)
          }
        }
      }
    }

    def assignTypeToParameterAndFixTypeParameters(parameter: Symbol, contextualType: Type, mapper: TypeMapper) {
      val links = getSymbolLinks(parameter)
      if (!links.type) {
        links.type = instantiateType(contextualType, mapper)
        assignBindingElementTypes(<ParameterDeclaration>parameter.valueDeclaration)
      }
      else if (isInferentialContext(mapper)) {
        // Even if the parameter already has a type, it might be because it was given a type while
        // processing the def as an argument to a prior signature during overload resolution.
        // If this was the case, it may have caused some type parameters to be fixed. So here,
        // we need to ensure that type parameters at the same positions get fixed again. This is
        // done by calling instantiateType to attach the mapper to the contextualType, and then
        // calling inferTypes to force a walk of contextualType so that all the correct fixing
        // happens. The choice to pass in links.type may seem kind of arbitrary, but it serves
        // to make sure that all the correct positions in contextualType are reached by the walk.
        // Here is an example:
        //
        //    trait Base {
        //      baseProp
        //    }
        //    trait Derived extends Base {
        //      toBase(): Base
        //    }
        //
        //    var derived: Derived
        //
        //    declare def foo<T>(x: T, func: (p: T) => T): T
        //    declare def foo<T>(x: T, func: (p: T) => T): T
        //
        //    var result = foo(derived, d => d.toBase())
        //
        // We are typing d while checking the second overload. But we've already given d
        // a type (Derived) from the first overload. However, we still want to fix the
        // T in the second overload so that we do not infer Base as a candidate for T
        // (inferring Base would make type argument inference inconsistent between the two
        // overloads).
        inferTypes(mapper.context, links.type, instantiateType(contextualType, mapper))
      }
    }

    def getReturnTypeFromJSDocComment(func: SignatureDeclaration | FunctionDeclaration): Type {
      val returnTag = getJSDocReturnTag(func)
      if (returnTag && returnTag.typeExpression) {
        return getTypeFromTypeNode(returnTag.typeExpression.type)
      }

      return ()
    }

    def createPromiseType(promisedType: Type): Type {
      // creates a `Promise<T>` type where `T` is the promisedType argument
      val globalPromiseType = getGlobalPromiseType()
      if (globalPromiseType != emptyGenericType) {
        // if the promised type is itself a promise, get the underlying type; otherwise, fallback to the promised type
        promisedType = getAwaitedType(promisedType)
        return createTypeReference(<GenericType>globalPromiseType, [promisedType])
      }

      return emptyObjectType
    }

    def getReturnTypeFromBody(func: FunctionLikeDeclaration, contextualMapper?: TypeMapper): Type {
      val contextualSignature = getContextualSignatureForFunctionLikeDeclaration(func)
      if (!func.body) {
        return unknownType
      }

      val isAsync = isAsyncFunctionLike(func)
      var type: Type
      if (func.body.kind != SyntaxKind.Block) {
        type = checkExpressionCached(<Expression>func.body, contextualMapper)
        if (isAsync) {
          // From within an async def you can return either a non-promise value or a promise. Any
          // Promise/A+ compatible implementation will always assimilate any foreign promise, so the
          // return type of the body should be unwrapped to its awaited type, which we will wrap in
          // the native Promise<T> type later in this def.
          type = checkAwaitedType(type, func, Diagnostics.Return_expression_in_async_function_does_not_have_a_valid_callable_then_member)
        }
      }
      else {
        var types: Type[]
        val funcIsGenerator = !!func.asteriskToken
        if (funcIsGenerator) {
          types = checkAndAggregateYieldOperandTypes(<Block>func.body, contextualMapper)
          if (types.length == 0) {
            val iterableIteratorAny = createIterableIteratorType(anyType)
            if (compilerOptions.noImplicitAny) {
              error(func.asteriskToken,
                Diagnostics.Generator_implicitly_has_type_0_because_it_does_not_yield_any_values_Consider_supplying_a_return_type, typeToString(iterableIteratorAny))
            }
            return iterableIteratorAny
          }
        }
        else {
          types = checkAndAggregateReturnExpressionTypes(<Block>func.body, contextualMapper, isAsync)
          if (types.length == 0) {
            if (isAsync) {
              // For an async def, the return type will not be Unit, but rather a Promise for Unit.
              val promiseType = createPromiseType(voidType)
              if (promiseType == emptyObjectType) {
                error(func, Diagnostics.An_async_function_or_method_must_have_a_valid_awaitable_return_type)
                return unknownType
              }

              return promiseType
            }
            else {
              return voidType
            }
          }
        }
        // When yield/return statements are contextually typed we allow the return type to be a union type.
        // Otherwise we require the yield/return expressions to have a best common supertype.
        type = contextualSignature ? getUnionType(types) : getCommonSupertype(types)
        if (!type) {
          if (funcIsGenerator) {
            error(func, Diagnostics.No_best_common_type_exists_among_yield_expressions)
            return createIterableIteratorType(unknownType)
          }
          else {
            error(func, Diagnostics.No_best_common_type_exists_among_return_expressions)
            // Defer to unioning the return types so we get a) downstream errors earlier and b) better Salsa experience
            return getUnionType(types)
          }
        }

        if (funcIsGenerator) {
          type = createIterableIteratorType(type)
        }
      }
      if (!contextualSignature) {
        reportErrorsFromWidening(func, type)
      }

      val widenedType = getWidenedType(type)
      if (isAsync) {
        // From within an async def you can return either a non-promise value or a promise. Any
        // Promise/A+ compatible implementation will always assimilate any foreign promise, so the
        // return type of the body is awaited type of the body, wrapped in a native Promise<T> type.
        val promiseType = createPromiseType(widenedType)
        if (promiseType == emptyObjectType) {
          error(func, Diagnostics.An_async_function_or_method_must_have_a_valid_awaitable_return_type)
          return unknownType
        }

        return promiseType
      }
      else {
        return widenedType
      }
    }

    def checkAndAggregateYieldOperandTypes(body: Block, contextualMapper?: TypeMapper): Type[] {
      val aggregatedTypes: Type[] = []

      forEachYieldExpression(body, yieldExpression => {
        val expr = yieldExpression.expression
        if (expr) {
          var type = checkExpressionCached(expr, contextualMapper)

          if (yieldExpression.asteriskToken) {
            // A yield* expression effectively yields everything that its operand yields
            type = checkElementTypeOfIterable(type, yieldExpression.expression)
          }

          if (!contains(aggregatedTypes, type)) {
            aggregatedTypes.push(type)
          }
        }
      })

      return aggregatedTypes
    }

    def checkAndAggregateReturnExpressionTypes(body: Block, contextualMapper?: TypeMapper, isAsync?: Boolean): Type[] {
      val aggregatedTypes: Type[] = []

      forEachReturnStatement(body, returnStatement => {
        val expr = returnStatement.expression
        if (expr) {
          var type = checkExpressionCached(expr, contextualMapper)
          if (isAsync) {
            // From within an async def you can return either a non-promise value or a promise. Any
            // Promise/A+ compatible implementation will always assimilate any foreign promise, so the
            // return type of the body should be unwrapped to its awaited type, which should be wrapped in
            // the native Promise<T> type by the caller.
            type = checkAwaitedType(type, body.parent, Diagnostics.Return_expression_in_async_function_does_not_have_a_valid_callable_then_member)
          }

          if (!contains(aggregatedTypes, type)) {
            aggregatedTypes.push(type)
          }
        }
      })

      return aggregatedTypes
    }

    /*
     *TypeScript Specification 1.0 (6.3) - July 2014
     * An explicitly typed def whose return type isn't the Void type,
     * the Any type, or a union type containing the Void or Any type as a constituent
     * must have at least one return statement somewhere in its body.
     * An exception to this rule is if the def implementation consists of a single 'throw' statement.
     * @param returnType - return type of the def, can be () if return type is not explicitly specified
     */
    def checkAllCodePathsInNonVoidFunctionReturnOrThrow(func: FunctionLikeDeclaration, returnType: Type): Unit {
      if (!produceDiagnostics) {
        return
      }

      // Functions with with an explicitly specified 'Unit' or 'any' return type don't need any return expressions.
      if (returnType && maybeTypeOfKind(returnType, TypeFlags.Any | TypeFlags.Void)) {
        return
      }

      // If all we have is a def signature, or an arrow def with an expression body, then there is nothing to check.
      // also if HasImplicitReturn flag is not set this means that all codepaths in def body end with return or throw
      if (nodeIsMissing(func.body) || func.body.kind != SyntaxKind.Block || !(func.flags & NodeFlags.HasImplicitReturn)) {
        return
      }

      val hasExplicitReturn = func.flags & NodeFlags.HasExplicitReturn

      if (returnType && !hasExplicitReturn) {
        // minimal check: def has syntactic return type annotation and no explicit return statements in the body
        // this def does not conform to the specification.
        // NOTE: having returnType != () is a precondition for entering this branch so func.type will always be present
        error(func.type, Diagnostics.A_function_whose_declared_type_is_neither_void_nor_any_must_return_a_value)
      }
      else if (compilerOptions.noImplicitReturns) {
        if (!returnType) {
          // If return type annotation is omitted check if def has any explicit return statements.
          // If it does not have any - its inferred return type is Unit - don't do any checks.
          // Otherwise get inferred return type from def body and report error only if it is not Unit / anytype
          val inferredReturnType = hasExplicitReturn
            ? getReturnTypeOfSignature(getSignatureFromDeclaration(func))
            : voidType

          if (inferredReturnType == voidType || isTypeAny(inferredReturnType)) {
            return
          }
        }
        error(func.type || func, Diagnostics.Not_all_code_paths_return_a_value)
      }
    }

    def checkFunctionExpressionOrObjectLiteralMethod(node: FunctionExpression | MethodDeclaration, contextualMapper?: TypeMapper): Type {
      Debug.assert(node.kind != SyntaxKind.MethodDeclaration || isObjectLiteralMethod(node))

      // Grammar checking
      val hasGrammarError = checkGrammarFunctionLikeDeclaration(node)
      if (!hasGrammarError && node.kind == SyntaxKind.FunctionExpression) {
        checkGrammarForGenerator(node)
      }

      // The identityMapper object is used to indicate that def expressions are wildcards
      if (contextualMapper == identityMapper && isContextSensitive(node)) {
        return anyFunctionType
      }

      val links = getNodeLinks(node)
      val type = getTypeOfSymbol(node.symbol)
      val contextSensitive = isContextSensitive(node)
      val mightFixTypeParameters = contextSensitive && isInferentialContext(contextualMapper)

      // Check if def expression is contextually typed and assign parameter types if so.
      // See the comment in assignTypeToParameterAndFixTypeParameters to understand why we need to
      // check mightFixTypeParameters.
      if (mightFixTypeParameters || !(links.flags & NodeCheckFlags.ContextChecked)) {
        val contextualSignature = getContextualSignature(node)
        // If a type check is started at a def expression that is an argument of a def call, obtaining the
        // contextual type may recursively get back to here during overload resolution of the call. If so, we will have
        // already assigned contextual types.
        val contextChecked = !!(links.flags & NodeCheckFlags.ContextChecked)
        if (mightFixTypeParameters || !contextChecked) {
          links.flags |= NodeCheckFlags.ContextChecked
          if (contextualSignature) {
            val signature = getSignaturesOfType(type, SignatureKind.Call)[0]
            if (contextSensitive) {
              assignContextualParameterTypes(signature, contextualSignature, contextualMapper || identityMapper)
            }
            if (mightFixTypeParameters || !node.type && !signature.resolvedReturnType) {
              val returnType = getReturnTypeFromBody(node, contextualMapper)
              if (!signature.resolvedReturnType) {
                signature.resolvedReturnType = returnType
              }
            }
          }

          if (!contextChecked) {
            checkSignatureDeclaration(node)
            checkNodeDeferred(node)
          }
        }
      }

      if (produceDiagnostics && node.kind != SyntaxKind.MethodDeclaration && node.kind != SyntaxKind.MethodSignature) {
        checkCollisionWithCapturedSuperVariable(node, (<FunctionExpression>node).name)
        checkCollisionWithCapturedThisVariable(node, (<FunctionExpression>node).name)
      }

      return type
    }

    def checkFunctionExpressionOrObjectLiteralMethodDeferred(node: ArrowFunction | FunctionExpression | MethodDeclaration) {
      Debug.assert(node.kind != SyntaxKind.MethodDeclaration || isObjectLiteralMethod(node))

      val isAsync = isAsyncFunctionLike(node)
      val returnOrPromisedType = node.type && (isAsync ? checkAsyncFunctionReturnType(node) : getTypeFromTypeNode(node.type))
      if (!node.asteriskToken) {
        // return is not necessary in the body of generators
        checkAllCodePathsInNonVoidFunctionReturnOrThrow(node, returnOrPromisedType)
      }

      if (node.body) {
        if (!node.type) {
          // There are some checks that are only performed in getReturnTypeFromBody, that may produce errors
          // we need. An example is the noImplicitAny errors resulting from widening the return expression
          // of a def. Because checking of def expression bodies is deferred, there was never an
          // appropriate time to do this during the main walk of the file (see the comment at the top of
          // checkFunctionExpressionBodies). So it must be done now.
          getReturnTypeOfSignature(getSignatureFromDeclaration(node))
        }

        if (node.body.kind == SyntaxKind.Block) {
          checkSourceElement(node.body)
        }
        else {
          // From within an async def you can return either a non-promise value or a promise. Any
          // Promise/A+ compatible implementation will always assimilate any foreign promise, so we
          // should not be checking assignability of a promise to the return type. Instead, we need to
          // check assignability of the awaited type of the expression body against the promised type of
          // its return type annotation.
          val exprType = checkExpression(<Expression>node.body)
          if (returnOrPromisedType) {
            if (isAsync) {
              val awaitedType = checkAwaitedType(exprType, node.body, Diagnostics.Expression_body_for_async_arrow_function_does_not_have_a_valid_callable_then_member)
              checkTypeAssignableTo(awaitedType, returnOrPromisedType, node.body)
            }
            else {
              checkTypeAssignableTo(exprType, returnOrPromisedType, node.body)
            }
          }
        }
      }
    }

    def checkArithmeticOperandType(operand: Node, type: Type, diagnostic: DiagnosticMessage): Boolean {
      if (!isTypeAnyOrAllConstituentTypesHaveKind(type, TypeFlags.NumberLike)) {
        error(operand, diagnostic)
        return false
      }
      return true
    }

    def isReadonlySymbol(symbol: Symbol): Boolean {
      // The following symbols are considered read-only:
      // Properties with a 'readonly' modifier
      // Variables declared with 'val'
      // Get accessors without matching set accessors
      // Enum members
      return symbol.flags & SymbolFlags.Property && (getDeclarationFlagsFromSymbol(symbol) & NodeFlags.Readonly) != 0 ||
        symbol.flags & SymbolFlags.Variable && (getDeclarationFlagsFromSymbol(symbol) & NodeFlags.Const) != 0 ||
        symbol.flags & SymbolFlags.Accessor && !(symbol.flags & SymbolFlags.SetAccessor) ||
        (symbol.flags & SymbolFlags.EnumMember) != 0
    }

    def isReferenceToReadonlyEntity(expr: Expression, symbol: Symbol): Boolean {
      if (isReadonlySymbol(symbol)) {
        // Allow assignments to readonly properties within constructors of the same class declaration.
        if (symbol.flags & SymbolFlags.Property &&
          (expr.kind == SyntaxKind.PropertyAccessExpression || expr.kind == SyntaxKind.ElementAccessExpression) &&
          (expr as PropertyAccessExpression | ElementAccessExpression).expression.kind == SyntaxKind.ThisKeyword) {
          val func = getContainingFunction(expr)
          return !(func && func.kind == SyntaxKind.Constructor && func.parent == symbol.valueDeclaration.parent)
        }
        return true
      }
      return false
    }

    def isReferenceThroughNamespaceImport(expr: Expression): Boolean {
      if (expr.kind == SyntaxKind.PropertyAccessExpression || expr.kind == SyntaxKind.ElementAccessExpression) {
        val node = skipParenthesizedNodes((expr as PropertyAccessExpression | ElementAccessExpression).expression)
        if (node.kind == SyntaxKind.Identifier) {
          val symbol = getNodeLinks(node).resolvedSymbol
          if (symbol.flags & SymbolFlags.Alias) {
            val declaration = getDeclarationOfAliasSymbol(symbol)
            return declaration && declaration.kind == SyntaxKind.NamespaceImport
          }
        }
      }
      return false
    }

    def checkReferenceExpression(expr: Expression, invalidReferenceMessage: DiagnosticMessage, constantVariableMessage: DiagnosticMessage): Boolean {
      // References are combinations of identifiers, parentheses, and property accesses.
      val node = skipParenthesizedNodes(expr)
      if (node.kind != SyntaxKind.Identifier && node.kind != SyntaxKind.PropertyAccessExpression && node.kind != SyntaxKind.ElementAccessExpression) {
        error(expr, invalidReferenceMessage)
        return false
      }
      // Because we get the symbol from the resolvedSymbol property, it might be of kind
      // SymbolFlags.ExportValue. In this case it is necessary to get the actual export
      // symbol, which will have the correct flags set on it.
      val links = getNodeLinks(node)
      val symbol = getExportSymbolOfValueSymbolIfExported(links.resolvedSymbol)
      if (symbol) {
        if (symbol != unknownSymbol && symbol != argumentsSymbol) {
          // Only variables (and not functions, classes, namespaces, enum objects, or enum members)
          // are considered references when referenced using a simple identifier.
          if (node.kind == SyntaxKind.Identifier && !(symbol.flags & SymbolFlags.Variable)) {
            error(expr, invalidReferenceMessage)
            return false
          }
          if (isReferenceToReadonlyEntity(node, symbol) || isReferenceThroughNamespaceImport(node)) {
            error(expr, constantVariableMessage)
            return false
          }
        }
      }
      else if (node.kind == SyntaxKind.ElementAccessExpression) {
        if (links.resolvedIndexInfo && links.resolvedIndexInfo.isReadonly) {
          error(expr, constantVariableMessage)
          return false
        }
      }
      return true
    }

    def checkDeleteExpression(node: DeleteExpression): Type {
      checkExpression(node.expression)
      return booleanType
    }

    def checkTypeOfExpression(node: TypeOfExpression): Type {
      checkExpression(node.expression)
      return stringType
    }

    def checkVoidExpression(node: VoidExpression): Type {
      checkExpression(node.expression)
      return undefinedType
    }

    def checkAwaitExpression(node: AwaitExpression): Type {
      // Grammar checking
      if (produceDiagnostics) {
        if (!(node.flags & NodeFlags.AwaitContext)) {
          grammarErrorOnFirstToken(node, Diagnostics.await_expression_is_only_allowed_within_an_async_function)
        }

        if (isInParameterInitializerBeforeContainingFunction(node)) {
          error(node, Diagnostics.await_expressions_cannot_be_used_in_a_parameter_initializer)
        }
      }

      val operandType = checkExpression(node.expression)
      return checkAwaitedType(operandType, node)
    }

    def checkPrefixUnaryExpression(node: PrefixUnaryExpression): Type {
      val operandType = checkExpression(node.operand)
      switch (node.operator) {
        case SyntaxKind.PlusToken:
        case SyntaxKind.MinusToken:
        case SyntaxKind.TildeToken:
          if (maybeTypeOfKind(operandType, TypeFlags.ESSymbol)) {
            error(node.operand, Diagnostics.The_0_operator_cannot_be_applied_to_type_symbol, tokenToString(node.operator))
          }
          return numberType
        case SyntaxKind.ExclamationToken:
          return booleanType
        case SyntaxKind.PlusPlusToken:
        case SyntaxKind.MinusMinusToken:
          val ok = checkArithmeticOperandType(node.operand, operandType, Diagnostics.An_arithmetic_operand_must_be_of_type_any_number_or_an_enum_type)
          if (ok) {
            // run check only if former checks succeeded to avoid reporting cascading errors
            checkReferenceExpression(node.operand,
              Diagnostics.The_operand_of_an_increment_or_decrement_operator_must_be_a_variable_property_or_indexer,
              Diagnostics.The_operand_of_an_increment_or_decrement_operator_cannot_be_a_constant_or_a_read_only_property)
          }
          return numberType
      }
      return unknownType
    }

    def checkPostfixUnaryExpression(node: PostfixUnaryExpression): Type {
      val operandType = checkExpression(node.operand)
      val ok = checkArithmeticOperandType(node.operand, operandType, Diagnostics.An_arithmetic_operand_must_be_of_type_any_number_or_an_enum_type)
      if (ok) {
        // run check only if former checks succeeded to avoid reporting cascading errors
        checkReferenceExpression(node.operand,
          Diagnostics.The_operand_of_an_increment_or_decrement_operator_must_be_a_variable_property_or_indexer,
          Diagnostics.The_operand_of_an_increment_or_decrement_operator_cannot_be_a_constant_or_a_read_only_property)
      }
      return numberType
    }

    // Return true if type might be of the given kind. A union or intersection type might be of a given
    // kind if at least one constituent type is of the given kind.
    def maybeTypeOfKind(type: Type, kind: TypeFlags): Boolean {
      if (type.flags & kind) {
        return true
      }
      if (type.flags & TypeFlags.UnionOrIntersection) {
        val types = (<UnionOrIntersectionType>type).types
        for (val t of types) {
          if (maybeTypeOfKind(t, kind)) {
            return true
          }
        }
      }
      return false
    }

    // Return true if type is of the given kind. A union type is of a given kind if all constituent types
    // are of the given kind. An intersection type is of a given kind if at least one constituent type is
    // of the given kind.
    def isTypeOfKind(type: Type, kind: TypeFlags): Boolean {
      if (type.flags & kind) {
        return true
      }
      if (type.flags & TypeFlags.Union) {
        val types = (<UnionOrIntersectionType>type).types
        for (val t of types) {
          if (!isTypeOfKind(t, kind)) {
            return false
          }
        }
        return true
      }
      if (type.flags & TypeFlags.Intersection) {
        val types = (<UnionOrIntersectionType>type).types
        for (val t of types) {
          if (isTypeOfKind(t, kind)) {
            return true
          }
        }
      }
      return false
    }

    def isConstEnumObjectType(type: Type): Boolean {
      return type.flags & (TypeFlags.ObjectType | TypeFlags.Anonymous) && type.symbol && isConstEnumSymbol(type.symbol)
    }

    def isConstEnumSymbol(symbol: Symbol): Boolean {
      return (symbol.flags & SymbolFlags.ConstEnum) != 0
    }

    def checkInstanceOfExpression(left: Expression, right: Expression, leftType: Type, rightType: Type): Type {
      // TypeScript 1.0 spec (April 2014): 4.15.4
      // The instanceof operator requires the left operand to be of type Any, an object type, or a type parameter type,
      // and the right operand to be of type Any or a subtype of the 'Function' trait type.
      // The result is always of the Boolean primitive type.
      // NOTE: do not raise error if leftType is unknown as related error was already reported
      if (isTypeOfKind(leftType, TypeFlags.Primitive)) {
        error(left, Diagnostics.The_left_hand_side_of_an_instanceof_expression_must_be_of_type_any_an_object_type_or_a_type_parameter)
      }
      // NOTE: do not raise error if right is unknown as related error was already reported
      if (!(isTypeAny(rightType) || isTypeSubtypeOf(rightType, globalFunctionType))) {
        error(right, Diagnostics.The_right_hand_side_of_an_instanceof_expression_must_be_of_type_any_or_of_a_type_assignable_to_the_Function_interface_type)
      }
      return booleanType
    }

    def checkInExpression(left: Expression, right: Expression, leftType: Type, rightType: Type): Type {
      // TypeScript 1.0 spec (April 2014): 4.15.5
      // The in operator requires the left operand to be of type Any, the String primitive type, or the Number primitive type,
      // and the right operand to be of type Any, an object type, or a type parameter type.
      // The result is always of the Boolean primitive type.
      if (!isTypeAnyOrAllConstituentTypesHaveKind(leftType, TypeFlags.StringLike | TypeFlags.NumberLike | TypeFlags.ESSymbol)) {
        error(left, Diagnostics.The_left_hand_side_of_an_in_expression_must_be_of_type_any_string_number_or_symbol)
      }
      if (!isTypeAnyOrAllConstituentTypesHaveKind(rightType, TypeFlags.ObjectType | TypeFlags.TypeParameter)) {
        error(right, Diagnostics.The_right_hand_side_of_an_in_expression_must_be_of_type_any_an_object_type_or_a_type_parameter)
      }
      return booleanType
    }

    def checkObjectLiteralAssignment(node: ObjectLiteralExpression, sourceType: Type, contextualMapper?: TypeMapper): Type {
      val properties = node.properties
      for (val p of properties) {
        if (p.kind == SyntaxKind.PropertyAssignment || p.kind == SyntaxKind.ShorthandPropertyAssignment) {
          val name = <PropertyName>(<PropertyAssignment>p).name
          if (name.kind == SyntaxKind.ComputedPropertyName) {
            checkComputedPropertyName(<ComputedPropertyName>name)
          }
          if (isComputedNonLiteralName(name)) {
            continue
          }

          val text = getTextOfPropertyName(name)
          val type = isTypeAny(sourceType)
            ? sourceType
            : getTypeOfPropertyOfType(sourceType, text) ||
            isNumericLiteralName(text) && getIndexTypeOfType(sourceType, IndexKind.Number) ||
            getIndexTypeOfType(sourceType, IndexKind.String)
          if (type) {
            if (p.kind == SyntaxKind.ShorthandPropertyAssignment) {
              checkDestructuringAssignment(<ShorthandPropertyAssignment>p, type)
            }
            else {
              // non-shorthand property assignments should always have initializers
              checkDestructuringAssignment((<PropertyAssignment>p).initializer, type)
            }
          }
          else {
            error(name, Diagnostics.Type_0_has_no_property_1_and_no_string_index_signature, typeToString(sourceType), declarationNameToString(name))
          }
        }
        else {
          error(p, Diagnostics.Property_assignment_expected)
        }
      }
      return sourceType
    }

    def checkArrayLiteralAssignment(node: ArrayLiteralExpression, sourceType: Type, contextualMapper?: TypeMapper): Type {
      // This elementType will be used if the specific property corresponding to this index is not
      // present (aka the tuple element property). This call also checks that the parentType is in
      // fact an iterable or array (depending on target language).
      val elementType = checkIteratedTypeOrElementType(sourceType, node, /*allowStringInput*/ false) || unknownType
      val elements = node.elements
      for (var i = 0; i < elements.length; i++) {
        val e = elements[i]
        if (e.kind != SyntaxKind.OmittedExpression) {
          if (e.kind != SyntaxKind.SpreadElementExpression) {
            val propName = "" + i
            val type = isTypeAny(sourceType)
              ? sourceType
              : isTupleLikeType(sourceType)
                ? getTypeOfPropertyOfType(sourceType, propName)
                : elementType
            if (type) {
              checkDestructuringAssignment(e, type, contextualMapper)
            }
            else {
              if (isTupleType(sourceType)) {
                error(e, Diagnostics.Tuple_type_0_with_length_1_cannot_be_assigned_to_tuple_with_length_2, typeToString(sourceType), (<TupleType>sourceType).elementTypes.length, elements.length)
              }
              else {
                error(e, Diagnostics.Type_0_has_no_property_1, typeToString(sourceType), propName)
              }
            }
          }
          else {
            if (i < elements.length - 1) {
              error(e, Diagnostics.A_rest_element_must_be_last_in_an_array_destructuring_pattern)
            }
            else {
              val restExpression = (<SpreadElementExpression>e).expression
              if (restExpression.kind == SyntaxKind.BinaryExpression && (<BinaryExpression>restExpression).operatorToken.kind == SyntaxKind.EqualsToken) {
                error((<BinaryExpression>restExpression).operatorToken, Diagnostics.A_rest_element_cannot_have_an_initializer)
              }
              else {
                checkDestructuringAssignment(restExpression, createArrayType(elementType), contextualMapper)
              }
            }
          }
        }
      }
      return sourceType
    }

    def checkDestructuringAssignment(exprOrAssignment: Expression | ShorthandPropertyAssignment, sourceType: Type, contextualMapper?: TypeMapper): Type {
      var target: Expression
      if (exprOrAssignment.kind == SyntaxKind.ShorthandPropertyAssignment) {
        val prop = <ShorthandPropertyAssignment>exprOrAssignment
        if (prop.objectAssignmentInitializer) {
          checkBinaryLikeExpression(prop.name, prop.equalsToken, prop.objectAssignmentInitializer, contextualMapper)
        }
        target = (<ShorthandPropertyAssignment>exprOrAssignment).name
      }
      else {
        target = <Expression>exprOrAssignment
      }

      if (target.kind == SyntaxKind.BinaryExpression && (<BinaryExpression>target).operatorToken.kind == SyntaxKind.EqualsToken) {
        checkBinaryExpression(<BinaryExpression>target, contextualMapper)
        target = (<BinaryExpression>target).left
      }
      if (target.kind == SyntaxKind.ObjectLiteralExpression) {
        return checkObjectLiteralAssignment(<ObjectLiteralExpression>target, sourceType, contextualMapper)
      }
      if (target.kind == SyntaxKind.ArrayLiteralExpression) {
        return checkArrayLiteralAssignment(<ArrayLiteralExpression>target, sourceType, contextualMapper)
      }
      return checkReferenceAssignment(target, sourceType, contextualMapper)
    }

    def checkReferenceAssignment(target: Expression, sourceType: Type, contextualMapper?: TypeMapper): Type {
      val targetType = checkExpression(target, contextualMapper)
      if (checkReferenceExpression(target, Diagnostics.Invalid_left_hand_side_of_assignment_expression, Diagnostics.Left_hand_side_of_assignment_expression_cannot_be_a_constant_or_a_read_only_property)) {
        checkTypeAssignableTo(sourceType, targetType, target, /*headMessage*/ ())
      }
      return sourceType
    }

    def checkBinaryExpression(node: BinaryExpression, contextualMapper?: TypeMapper) {
      return checkBinaryLikeExpression(node.left, node.operatorToken, node.right, contextualMapper, node)
    }

    def checkBinaryLikeExpression(left: Expression, operatorToken: Node, right: Expression, contextualMapper?: TypeMapper, errorNode?: Node) {
      val operator = operatorToken.kind
      if (operator == SyntaxKind.EqualsToken && (left.kind == SyntaxKind.ObjectLiteralExpression || left.kind == SyntaxKind.ArrayLiteralExpression)) {
        return checkDestructuringAssignment(left, checkExpression(right, contextualMapper), contextualMapper)
      }
      var leftType = checkExpression(left, contextualMapper)
      var rightType = checkExpression(right, contextualMapper)
      switch (operator) {
        case SyntaxKind.AsteriskToken:
        case SyntaxKind.AsteriskAsteriskToken:
        case SyntaxKind.AsteriskEqualsToken:
        case SyntaxKind.AsteriskAsteriskEqualsToken:
        case SyntaxKind.SlashToken:
        case SyntaxKind.SlashEqualsToken:
        case SyntaxKind.PercentToken:
        case SyntaxKind.PercentEqualsToken:
        case SyntaxKind.MinusToken:
        case SyntaxKind.MinusEqualsToken:
        case SyntaxKind.LessThanLessThanToken:
        case SyntaxKind.LessThanLessThanEqualsToken:
        case SyntaxKind.GreaterThanGreaterThanToken:
        case SyntaxKind.GreaterThanGreaterThanEqualsToken:
        case SyntaxKind.GreaterThanGreaterThanGreaterThanToken:
        case SyntaxKind.GreaterThanGreaterThanGreaterThanEqualsToken:
        case SyntaxKind.BarToken:
        case SyntaxKind.BarEqualsToken:
        case SyntaxKind.CaretToken:
        case SyntaxKind.CaretEqualsToken:
        case SyntaxKind.AmpersandToken:
        case SyntaxKind.AmpersandEqualsToken:
          // TypeScript 1.0 spec (April 2014): 4.19.1
          // These operators require their operands to be of type Any, the Number primitive type,
          // or an enum type. Operands of an enum type are treated
          // as having the primitive type Number. If one operand is the null or () value,
          // it is treated as having the type of the other operand.
          // The result is always of the Number primitive type.
          if (leftType.flags & (TypeFlags.Undefined | TypeFlags.Null)) leftType = rightType
          if (rightType.flags & (TypeFlags.Undefined | TypeFlags.Null)) rightType = leftType

          var suggestedOperator: SyntaxKind
          // if a user tries to apply a bitwise operator to 2 Boolean operands
          // try and return them a helpful suggestion
          if ((leftType.flags & TypeFlags.Boolean) &&
            (rightType.flags & TypeFlags.Boolean) &&
            (suggestedOperator = getSuggestedBooleanOperator(operatorToken.kind)) != ()) {
            error(errorNode || operatorToken, Diagnostics.The_0_operator_is_not_allowed_for_boolean_types_Consider_using_1_instead, tokenToString(operatorToken.kind), tokenToString(suggestedOperator))
          }
          else {
            // otherwise just check each operand separately and report errors as normal
            val leftOk = checkArithmeticOperandType(left, leftType, Diagnostics.The_left_hand_side_of_an_arithmetic_operation_must_be_of_type_any_number_or_an_enum_type)
            val rightOk = checkArithmeticOperandType(right, rightType, Diagnostics.The_right_hand_side_of_an_arithmetic_operation_must_be_of_type_any_number_or_an_enum_type)
            if (leftOk && rightOk) {
              checkAssignmentOperator(numberType)
            }
          }

          return numberType
        case SyntaxKind.PlusToken:
        case SyntaxKind.PlusEqualsToken:
          // TypeScript 1.0 spec (April 2014): 4.19.2
          // The binary + operator requires both operands to be of the Number primitive type or an enum type,
          // or at least one of the operands to be of type Any or the String primitive type.

          // If one operand is the null or () value, it is treated as having the type of the other operand.
          if (leftType.flags & (TypeFlags.Undefined | TypeFlags.Null)) leftType = rightType
          if (rightType.flags & (TypeFlags.Undefined | TypeFlags.Null)) rightType = leftType

          var resultType: Type
          if (isTypeOfKind(leftType, TypeFlags.NumberLike) && isTypeOfKind(rightType, TypeFlags.NumberLike)) {
            // Operands of an enum type are treated as having the primitive type Number.
            // If both operands are of the Number primitive type, the result is of the Number primitive type.
            resultType = numberType
          }
          else {
            if (isTypeOfKind(leftType, TypeFlags.StringLike) || isTypeOfKind(rightType, TypeFlags.StringLike)) {
              // If one or both operands are of the String primitive type, the result is of the String primitive type.
              resultType = stringType
            }
            else if (isTypeAny(leftType) || isTypeAny(rightType)) {
              // Otherwise, the result is of type Any.
              // NOTE: unknown type here denotes error type. Old compiler treated this case as any type so do we.
              resultType = leftType == unknownType || rightType == unknownType ? unknownType : anyType
            }

            // Symbols are not allowed at all in arithmetic expressions
            if (resultType && !checkForDisallowedESSymbolOperand(operator)) {
              return resultType
            }
          }

          if (!resultType) {
            reportOperatorError()
            return anyType
          }

          if (operator == SyntaxKind.PlusEqualsToken) {
            checkAssignmentOperator(resultType)
          }
          return resultType
        case SyntaxKind.LessThanToken:
        case SyntaxKind.GreaterThanToken:
        case SyntaxKind.LessThanEqualsToken:
        case SyntaxKind.GreaterThanEqualsToken:
          if (!checkForDisallowedESSymbolOperand(operator)) {
            return booleanType
          }
        // Fall through
        case SyntaxKind.EqualsEqualsToken:
        case SyntaxKind.ExclamationEqualsToken:
        case SyntaxKind.EqualsEqualsEqualsToken:
        case SyntaxKind.ExclamationEqualsEqualsToken:
          // Permit 'Int[] | "foo"' to be asserted to 'String'.
          if (maybeTypeOfKind(leftType, TypeFlags.StringLike) && maybeTypeOfKind(rightType, TypeFlags.StringLike)) {
            return booleanType
          }
          if (!isTypeAssignableTo(leftType, rightType) && !isTypeAssignableTo(rightType, leftType)) {
            reportOperatorError()
          }
          return booleanType
        case SyntaxKind.InstanceOfKeyword:
          return checkInstanceOfExpression(left, right, leftType, rightType)
        case SyntaxKind.InKeyword:
          return checkInExpression(left, right, leftType, rightType)
        case SyntaxKind.AmpersandAmpersandToken:
          return rightType
        case SyntaxKind.BarBarToken:
          return getUnionType([leftType, rightType])
        case SyntaxKind.EqualsToken:
          checkAssignmentOperator(rightType)
          return getRegularTypeOfObjectLiteral(rightType)
        case SyntaxKind.CommaToken:
          return rightType
      }

      // Return true if there was no error, false if there was an error.
      def checkForDisallowedESSymbolOperand(operator: SyntaxKind): Boolean {
        val offendingSymbolOperand =
          maybeTypeOfKind(leftType, TypeFlags.ESSymbol) ? left :
            maybeTypeOfKind(rightType, TypeFlags.ESSymbol) ? right :
              ()
        if (offendingSymbolOperand) {
          error(offendingSymbolOperand, Diagnostics.The_0_operator_cannot_be_applied_to_type_symbol, tokenToString(operator))
          return false
        }

        return true
      }

      def getSuggestedBooleanOperator(operator: SyntaxKind): SyntaxKind {
        switch (operator) {
          case SyntaxKind.BarToken:
          case SyntaxKind.BarEqualsToken:
            return SyntaxKind.BarBarToken
          case SyntaxKind.CaretToken:
          case SyntaxKind.CaretEqualsToken:
            return SyntaxKind.ExclamationEqualsEqualsToken
          case SyntaxKind.AmpersandToken:
          case SyntaxKind.AmpersandEqualsToken:
            return SyntaxKind.AmpersandAmpersandToken
          default:
            return ()
        }
      }

      def checkAssignmentOperator(valueType: Type): Unit {
        if (produceDiagnostics && operator >= SyntaxKind.FirstAssignment && operator <= SyntaxKind.LastAssignment) {
          // TypeScript 1.0 spec (April 2014): 4.17
          // An assignment of the form
          //  VarExpr = ValueExpr
          // requires VarExpr to be classified as a reference
          // A compound assignment furthermore requires VarExpr to be classified as a reference (section 4.1)
          // and the type of the non - compound operation to be assignable to the type of VarExpr.
          val ok = checkReferenceExpression(left,
            Diagnostics.Invalid_left_hand_side_of_assignment_expression,
            Diagnostics.Left_hand_side_of_assignment_expression_cannot_be_a_constant_or_a_read_only_property)
          // Use default messages
          if (ok) {
            // to avoid cascading errors check assignability only if 'isReference' check succeeded and no errors were reported
            checkTypeAssignableTo(valueType, leftType, left, /*headMessage*/ ())
          }
        }
      }

      def reportOperatorError() {
        error(errorNode || operatorToken, Diagnostics.Operator_0_cannot_be_applied_to_types_1_and_2, tokenToString(operatorToken.kind), typeToString(leftType), typeToString(rightType))
      }
    }

    def isYieldExpressionInClass(node: YieldExpression): Boolean {
      var current: Node = node
      var parent = node.parent
      while (parent) {
        if (isFunctionLike(parent) && current == (<FunctionLikeDeclaration>parent).body) {
          return false
        }
        else if (isClassLike(current)) {
          return true
        }

        current = parent
        parent = parent.parent
      }

      return false
    }

    def checkYieldExpression(node: YieldExpression): Type {
      // Grammar checking
      if (produceDiagnostics) {
        if (!(node.flags & NodeFlags.YieldContext) || isYieldExpressionInClass(node)) {
          grammarErrorOnFirstToken(node, Diagnostics.A_yield_expression_is_only_allowed_in_a_generator_body)
        }

        if (isInParameterInitializerBeforeContainingFunction(node)) {
          error(node, Diagnostics.yield_expressions_cannot_be_used_in_a_parameter_initializer)
        }
      }

      if (node.expression) {
        val func = getContainingFunction(node)
        // If the user's code is syntactically correct, the func should always have a star. After all,
        // we are in a yield context.
        if (func && func.asteriskToken) {
          val expressionType = checkExpressionCached(node.expression, /*contextualMapper*/ ())
          var expressionElementType: Type
          val nodeIsYieldStar = !!node.asteriskToken
          if (nodeIsYieldStar) {
            expressionElementType = checkElementTypeOfIterable(expressionType, node.expression)
          }
          // There is no point in doing an assignability check if the def
          // has no explicit return type because the return type is directly computed
          // from the yield expressions.
          if (func.type) {
            val signatureElementType = getElementTypeOfIterableIterator(getTypeFromTypeNode(func.type)) || anyType
            if (nodeIsYieldStar) {
              checkTypeAssignableTo(expressionElementType, signatureElementType, node.expression, /*headMessage*/ ())
            }
            else {
              checkTypeAssignableTo(expressionType, signatureElementType, node.expression, /*headMessage*/ ())
            }
          }
        }
      }

      // Both yield and yield* expressions have type 'any'
      return anyType
    }

    def checkConditionalExpression(node: ConditionalExpression, contextualMapper?: TypeMapper): Type {
      checkExpression(node.condition)
      val type1 = checkExpression(node.whenTrue, contextualMapper)
      val type2 = checkExpression(node.whenFalse, contextualMapper)
      return getUnionType([type1, type2])
    }

    def checkStringLiteralExpression(node: StringLiteral): Type {
      val contextualType = getContextualType(node)
      if (contextualType && contextualTypeIsStringLiteralType(contextualType)) {
        return getStringLiteralTypeForText(node.text)
      }

      return stringType
    }

    def checkTemplateExpression(node: TemplateExpression): Type {
      // We just want to check each expressions, but we are unconcerned with
      // the type of each expression, as any value may be coerced into a String.
      // It is worth asking whether this is what we really want though.
      // A place where we actually *are* concerned with the expressions' types are
      // in tagged templates.
      forEach((<TemplateExpression>node).templateSpans, templateSpan => {
        checkExpression(templateSpan.expression)
      })

      return stringType
    }

    def checkExpressionWithContextualType(node: Expression, contextualType: Type, contextualMapper?: TypeMapper): Type {
      val saveContextualType = node.contextualType
      node.contextualType = contextualType
      val result = checkExpression(node, contextualMapper)
      node.contextualType = saveContextualType
      return result
    }

    def checkExpressionCached(node: Expression, contextualMapper?: TypeMapper): Type {
      val links = getNodeLinks(node)
      if (!links.resolvedType) {
        links.resolvedType = checkExpression(node, contextualMapper)
      }
      return links.resolvedType
    }

    def checkPropertyAssignment(node: PropertyAssignment, contextualMapper?: TypeMapper): Type {
      // Do not use hasDynamicName here, because that returns false for well known symbols.
      // We want to perform checkComputedPropertyName for all computed properties, including
      // well known symbols.
      if (node.name.kind == SyntaxKind.ComputedPropertyName) {
        checkComputedPropertyName(<ComputedPropertyName>node.name)
      }

      return checkExpression((<PropertyAssignment>node).initializer, contextualMapper)
    }

    def checkObjectLiteralMethod(node: MethodDeclaration, contextualMapper?: TypeMapper): Type {
      // Grammar checking
      checkGrammarMethod(node)

      // Do not use hasDynamicName here, because that returns false for well known symbols.
      // We want to perform checkComputedPropertyName for all computed properties, including
      // well known symbols.
      if (node.name.kind == SyntaxKind.ComputedPropertyName) {
        checkComputedPropertyName(<ComputedPropertyName>node.name)
      }

      val uninstantiatedType = checkFunctionExpressionOrObjectLiteralMethod(node, contextualMapper)
      return instantiateTypeWithSingleGenericCallSignature(node, uninstantiatedType, contextualMapper)
    }

    def instantiateTypeWithSingleGenericCallSignature(node: Expression | MethodDeclaration, type: Type, contextualMapper?: TypeMapper) {
      if (isInferentialContext(contextualMapper)) {
        val signature = getSingleCallSignature(type)
        if (signature && signature.typeParameters) {
          val contextualType = getApparentTypeOfContextualType(<Expression>node)
          if (contextualType) {
            val contextualSignature = getSingleCallSignature(contextualType)
            if (contextualSignature && !contextualSignature.typeParameters) {
              return getOrCreateTypeFromSignature(instantiateSignatureInContextOf(signature, contextualSignature, contextualMapper))
            }
          }
        }
      }

      return type
    }

    // Checks an expression and returns its type. The contextualMapper parameter serves two purposes: When
    // contextualMapper is not () and not equal to the identityMapper def object it indicates that the
    // expression is being inferentially typed (section 4.12.2 in spec) and provides the type mapper to use in
    // conjunction with the generic contextual type. When contextualMapper is equal to the identityMapper def
    // object, it serves as an indicator that all contained def and arrow expressions should be considered to
    // have the wildcard def type; this form of type check is used during overload resolution to exclude
    // contextually typed def and arrow expressions in the initial phase.
    def checkExpression(node: Expression | QualifiedName, contextualMapper?: TypeMapper): Type {
      var type: Type
      if (node.kind == SyntaxKind.QualifiedName) {
        type = checkQualifiedName(<QualifiedName>node)
      }
      else {
        val uninstantiatedType = checkExpressionWorker(<Expression>node, contextualMapper)
        type = instantiateTypeWithSingleGenericCallSignature(<Expression>node, uninstantiatedType, contextualMapper)
      }

      if (isConstEnumObjectType(type)) {
        // enum object type for val enums are only permitted in:
        // - 'left' in property access
        // - 'object' in indexed access
        // - target in rhs of import statement
        val ok =
          (node.parent.kind == SyntaxKind.PropertyAccessExpression && (<PropertyAccessExpression>node.parent).expression == node) ||
          (node.parent.kind == SyntaxKind.ElementAccessExpression && (<ElementAccessExpression>node.parent).expression == node) ||
          ((node.kind == SyntaxKind.Identifier || node.kind == SyntaxKind.QualifiedName) && isInRightSideOfImportOrExportAssignment(<Identifier>node))

        if (!ok) {
          error(node, Diagnostics.const_enums_can_only_be_used_in_property_or_index_access_expressions_or_the_right_hand_side_of_an_import_declaration_or_export_assignment)
        }
      }
      return type
    }

    def checkNumericLiteral(node: LiteralExpression): Type {
      // Grammar checking
      checkGrammarNumericLiteral(node)
      return numberType
    }

    def checkExpressionWorker(node: Expression, contextualMapper: TypeMapper): Type {
      switch (node.kind) {
        case SyntaxKind.Identifier:
          return checkIdentifier(<Identifier>node)
        case SyntaxKind.ThisKeyword:
          return checkThisExpression(node)
        case SyntaxKind.SuperKeyword:
          return checkSuperExpression(node)
        case SyntaxKind.NullKeyword:
          return nullType
        case SyntaxKind.TrueKeyword:
        case SyntaxKind.FalseKeyword:
          return booleanType
        case SyntaxKind.NumericLiteral:
          return checkNumericLiteral(<LiteralExpression>node)
        case SyntaxKind.TemplateExpression:
          return checkTemplateExpression(<TemplateExpression>node)
        case SyntaxKind.StringLiteral:
          return checkStringLiteralExpression(<StringLiteral>node)
        case SyntaxKind.NoSubstitutionTemplateLiteral:
          return stringType
        case SyntaxKind.RegularExpressionLiteral:
          return globalRegExpType
        case SyntaxKind.ArrayLiteralExpression:
          return checkArrayLiteral(<ArrayLiteralExpression>node, contextualMapper)
        case SyntaxKind.ObjectLiteralExpression:
          return checkObjectLiteral(<ObjectLiteralExpression>node, contextualMapper)
        case SyntaxKind.PropertyAccessExpression:
          return checkPropertyAccessExpression(<PropertyAccessExpression>node)
        case SyntaxKind.ElementAccessExpression:
          return checkIndexedAccess(<ElementAccessExpression>node)
        case SyntaxKind.CallExpression:
        case SyntaxKind.NewExpression:
          return checkCallExpression(<CallExpression>node)
        case SyntaxKind.TaggedTemplateExpression:
          return checkTaggedTemplateExpression(<TaggedTemplateExpression>node)
        case SyntaxKind.ParenthesizedExpression:
          return checkExpression((<ParenthesizedExpression>node).expression, contextualMapper)
        case SyntaxKind.ClassExpression:
          return checkClassExpression(<ClassExpression>node)
        case SyntaxKind.FunctionExpression:
        case SyntaxKind.ArrowFunction:
          return checkFunctionExpressionOrObjectLiteralMethod(<FunctionExpression>node, contextualMapper)
        case SyntaxKind.TypeOfExpression:
          return checkTypeOfExpression(<TypeOfExpression>node)
        case SyntaxKind.TypeAssertionExpression:
        case SyntaxKind.AsExpression:
          return checkAssertion(<AssertionExpression>node)
        case SyntaxKind.DeleteExpression:
          return checkDeleteExpression(<DeleteExpression>node)
        case SyntaxKind.VoidExpression:
          return checkVoidExpression(<VoidExpression>node)
        case SyntaxKind.AwaitExpression:
          return checkAwaitExpression(<AwaitExpression>node)
        case SyntaxKind.PrefixUnaryExpression:
          return checkPrefixUnaryExpression(<PrefixUnaryExpression>node)
        case SyntaxKind.PostfixUnaryExpression:
          return checkPostfixUnaryExpression(<PostfixUnaryExpression>node)
        case SyntaxKind.BinaryExpression:
          return checkBinaryExpression(<BinaryExpression>node, contextualMapper)
        case SyntaxKind.ConditionalExpression:
          return checkConditionalExpression(<ConditionalExpression>node, contextualMapper)
        case SyntaxKind.SpreadElementExpression:
          return checkSpreadElementExpression(<SpreadElementExpression>node, contextualMapper)
        case SyntaxKind.OmittedExpression:
          return undefinedType
        case SyntaxKind.YieldExpression:
          return checkYieldExpression(<YieldExpression>node)
        case SyntaxKind.JsxExpression:
          return checkJsxExpression(<JsxExpression>node)
        case SyntaxKind.JsxElement:
          return checkJsxElement(<JsxElement>node)
        case SyntaxKind.JsxSelfClosingElement:
          return checkJsxSelfClosingElement(<JsxSelfClosingElement>node)
        case SyntaxKind.JsxOpeningElement:
          Debug.fail("Shouldn't ever directly check a JsxOpeningElement")
      }
      return unknownType
    }

    // DECLARATION AND STATEMENT TYPE CHECKING

    def checkTypeParameter(node: TypeParameterDeclaration) {
      // Grammar Checking
      if (node.expression) {
        grammarErrorOnFirstToken(node.expression, Diagnostics.Type_expected)
      }

      checkSourceElement(node.constraint)
      getConstraintOfTypeParameter(getDeclaredTypeOfTypeParameter(getSymbolOfNode(node)))
      if (produceDiagnostics) {
        checkTypeNameIsReserved(node.name, Diagnostics.Type_parameter_name_cannot_be_0)
      }
    }

    def checkParameter(node: ParameterDeclaration) {
      // Grammar checking
      // It is a SyntaxError if the Identifier "eval" or the Identifier "arguments" occurs as the
      // Identifier in a PropertySetParameterList of a PropertyAssignment that is contained in strict code
      // or if its FunctionBody is strict code(11.1.5).

      // Grammar checking
      checkGrammarDecorators(node) || checkGrammarModifiers(node)

      checkVariableLikeDeclaration(node)
      var func = getContainingFunction(node)
      if (node.flags & NodeFlags.AccessibilityModifier) {
        func = getContainingFunction(node)
        if (!(func.kind == SyntaxKind.Constructor && nodeIsPresent(func.body))) {
          error(node, Diagnostics.A_parameter_property_is_only_allowed_in_a_constructor_implementation)
        }
      }
      if (node.questionToken && isBindingPattern(node.name) && func.body) {
        error(node, Diagnostics.A_binding_pattern_parameter_cannot_be_optional_in_an_implementation_signature)
      }

      // Only check rest parameter type if it's not a binding pattern. Since binding patterns are
      // not allowed in a rest parameter, we already have an error from checkGrammarParameterList.
      if (node.dotDotDotToken && !isBindingPattern(node.name) && !isArrayType(getTypeOfSymbol(node.symbol))) {
        error(node, Diagnostics.A_rest_parameter_must_be_of_an_array_type)
      }
    }

    def isSyntacticallyValidGenerator(node: SignatureDeclaration): Boolean {
      if (!(<FunctionLikeDeclaration>node).asteriskToken || !(<FunctionLikeDeclaration>node).body) {
        return false
      }

      return node.kind == SyntaxKind.MethodDeclaration ||
        node.kind == SyntaxKind.FunctionDeclaration ||
        node.kind == SyntaxKind.FunctionExpression
    }

    def getTypePredicateParameterIndex(parameterList: NodeArray<ParameterDeclaration>, parameter: Identifier): Int {
      if (parameterList) {
        for (var i = 0; i < parameterList.length; i++) {
          val param = parameterList[i]
          if (param.name.kind == SyntaxKind.Identifier &&
            (<Identifier>param.name).text == parameter.text) {

            return i
          }
        }
      }
      return -1
    }

    def checkTypePredicate(node: TypePredicateNode) {
      val parent = getTypePredicateParent(node)
      if (!parent) {
        return
      }
      val returnType = getReturnTypeOfSignature(getSignatureFromDeclaration(parent))
      if (!returnType || !(returnType.flags & TypeFlags.PredicateType)) {
        return
      }
      val { parameterName } = node
      if (parameterName.kind == SyntaxKind.ThisType) {
        getTypeFromThisTypeNode(parameterName as ThisTypeNode)
      }
      else {
        val typePredicate = <IdentifierTypePredicate>(<PredicateType>returnType).predicate
        if (typePredicate.parameterIndex >= 0) {
          if (parent.parameters[typePredicate.parameterIndex].dotDotDotToken) {
            error(parameterName,
              Diagnostics.A_type_predicate_cannot_reference_a_rest_parameter)
          }
          else {
            checkTypeAssignableTo(typePredicate.type,
              getTypeOfNode(parent.parameters[typePredicate.parameterIndex]),
              node.type)
          }
        }
        else if (parameterName) {
          var hasReportedError = false
          for (val { name } of parent.parameters) {
            if ((name.kind == SyntaxKind.ObjectBindingPattern ||
              name.kind == SyntaxKind.ArrayBindingPattern) &&
              checkIfTypePredicateVariableIsDeclaredInBindingPattern(
                <BindingPattern>name,
                parameterName,
                typePredicate.parameterName)) {
              hasReportedError = true
              break
            }
          }
          if (!hasReportedError) {
            error(node.parameterName, Diagnostics.Cannot_find_parameter_0, typePredicate.parameterName)
          }
        }
      }
    }

    def getTypePredicateParent(node: Node): SignatureDeclaration {
      switch (node.parent.kind) {
        case SyntaxKind.ArrowFunction:
        case SyntaxKind.CallSignature:
        case SyntaxKind.FunctionDeclaration:
        case SyntaxKind.FunctionExpression:
        case SyntaxKind.FunctionType:
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.MethodSignature:
          val parent = <SignatureDeclaration>node.parent
          if (node == parent.type) {
            return parent
          }
      }
    }

    def checkIfTypePredicateVariableIsDeclaredInBindingPattern(
      pattern: BindingPattern,
      predicateVariableNode: Node,
      predicateVariableName: String) {
      for (val { name } of pattern.elements) {
        if (name.kind == SyntaxKind.Identifier &&
          (<Identifier>name).text == predicateVariableName) {
          error(predicateVariableNode,
            Diagnostics.A_type_predicate_cannot_reference_element_0_in_a_binding_pattern,
            predicateVariableName)
          return true
        }
        else if (name.kind == SyntaxKind.ArrayBindingPattern ||
          name.kind == SyntaxKind.ObjectBindingPattern) {
          if (checkIfTypePredicateVariableIsDeclaredInBindingPattern(
            <BindingPattern>name,
            predicateVariableNode,
             predicateVariableName)) {
            return true
          }
        }
      }
    }

    def checkSignatureDeclaration(node: SignatureDeclaration) {
      // Grammar checking
      if (node.kind == SyntaxKind.IndexSignature) {
        checkGrammarIndexSignature(<SignatureDeclaration>node)
      }
      // TODO (yuisu): Remove this check in else-if when SyntaxKind.Construct is moved and ambient context is handled
      else if (node.kind == SyntaxKind.FunctionType || node.kind == SyntaxKind.FunctionDeclaration || node.kind == SyntaxKind.ConstructorType ||
        node.kind == SyntaxKind.CallSignature || node.kind == SyntaxKind.Constructor ||
        node.kind == SyntaxKind.ConstructSignature) {
        checkGrammarFunctionLikeDeclaration(<FunctionLikeDeclaration>node)
      }

      checkTypeParameters(node.typeParameters)

      forEach(node.parameters, checkParameter)

      checkSourceElement(node.type)


      if (produceDiagnostics) {
        checkCollisionWithArgumentsInGeneratedCode(node)
        if (compilerOptions.noImplicitAny && !node.type) {
          switch (node.kind) {
            case SyntaxKind.ConstructSignature:
              error(node, Diagnostics.Construct_signature_which_lacks_return_type_annotation_implicitly_has_an_any_return_type)
              break
            case SyntaxKind.CallSignature:
              error(node, Diagnostics.Call_signature_which_lacks_return_type_annotation_implicitly_has_an_any_return_type)
              break
          }
        }

        if (node.type) {
          if (languageVersion >= ScriptTarget.ES6 && isSyntacticallyValidGenerator(node)) {
            val returnType = getTypeFromTypeNode(node.type)
            if (returnType == voidType) {
              error(node.type, Diagnostics.A_generator_cannot_have_a_void_type_annotation)
            }
            else {
              val generatorElementType = getElementTypeOfIterableIterator(returnType) || anyType
              val iterableIteratorInstantiation = createIterableIteratorType(generatorElementType)

              // Naively, one could check that IterableIterator<any> is assignable to the return type annotation.
              // However, that would not catch the error in the following case.
              //
              //  trait BadGenerator extends Iterable<Int>, Iterator<String> { }
              //  def* g(): BadGenerator { } // Iterable and Iterator have different types!
              //
              checkTypeAssignableTo(iterableIteratorInstantiation, returnType, node.type)
            }
          }
          else if (isAsyncFunctionLike(node)) {
            checkAsyncFunctionReturnType(<FunctionLikeDeclaration>node)
          }
        }
      }
    }

    def checkTypeForDuplicateIndexSignatures(node: Node) {
      if (node.kind == SyntaxKind.InterfaceDeclaration) {
        val nodeSymbol = getSymbolOfNode(node)
        // in case of merging trait declaration it is possible that we'll enter this check procedure several times for every declaration
        // to prevent this run check only for the first declaration of a given kind
        if (nodeSymbol.declarations.length > 0 && nodeSymbol.declarations[0] != node) {
          return
        }
      }

      // TypeScript 1.0 spec (April 2014)
      // 3.7.4: An object type can contain at most one String index signature and one numeric index signature.
      // 8.5: A class declaration can have at most one String index member declaration and one numeric index member declaration
      val indexSymbol = getIndexSymbol(getSymbolOfNode(node))
      if (indexSymbol) {
        var seenNumericIndexer = false
        var seenStringIndexer = false
        for (val decl of indexSymbol.declarations) {
          val declaration = <SignatureDeclaration>decl
          if (declaration.parameters.length == 1 && declaration.parameters[0].type) {
            switch (declaration.parameters[0].type.kind) {
              case SyntaxKind.StringKeyword:
                if (!seenStringIndexer) {
                  seenStringIndexer = true
                }
                else {
                  error(declaration, Diagnostics.Duplicate_string_index_signature)
                }
                break
              case SyntaxKind.NumberKeyword:
                if (!seenNumericIndexer) {
                  seenNumericIndexer = true
                }
                else {
                  error(declaration, Diagnostics.Duplicate_number_index_signature)
                }
                break
            }
          }
        }
      }
    }

    def checkPropertyDeclaration(node: PropertyDeclaration) {
      // Grammar checking
      checkGrammarDecorators(node) || checkGrammarModifiers(node) || checkGrammarProperty(node) || checkGrammarComputedPropertyName(node.name)

      checkVariableLikeDeclaration(node)
    }

    def checkMethodDeclaration(node: MethodDeclaration) {
      // Grammar checking
      checkGrammarMethod(node) || checkGrammarComputedPropertyName(node.name)

      // Grammar checking for modifiers is done inside the def checkGrammarFunctionLikeDeclaration
      checkFunctionOrMethodDeclaration(node)

      // Abstract methods cannot have an implementation.
      // Extra checks are to avoid reporting multiple errors relating to the "abstractness" of the node.
      if (node.flags & NodeFlags.Abstract && node.body) {
        error(node, Diagnostics.Method_0_cannot_have_an_implementation_because_it_is_marked_abstract, declarationNameToString(node.name))
      }
    }

    def checkConstructorDeclaration(node: ConstructorDeclaration) {
      // Grammar check on signature of constructor and modifier of the constructor is done in checkSignatureDeclaration def.
      checkSignatureDeclaration(node)
      // Grammar check for checking only related to constructorDeclaration
      checkGrammarConstructorTypeParameters(node) || checkGrammarConstructorTypeAnnotation(node)

      checkSourceElement(node.body)

      val symbol = getSymbolOfNode(node)
      val firstDeclaration = getDeclarationOfKind(symbol, node.kind)

      // Only type check the symbol once
      if (node == firstDeclaration) {
        checkFunctionOrConstructorSymbol(symbol)
      }

      // exit early in the case of signature - super checks are not relevant to them
      if (nodeIsMissing(node.body)) {
        return
      }

      if (!produceDiagnostics) {
        return
      }

      def containsSuperCallAsComputedPropertyName(n: Declaration): Boolean {
        return n.name && containsSuperCall(n.name)
      }

      def containsSuperCall(n: Node): Boolean {
        if (isSuperCallExpression(n)) {
          return true
        }
        else if (isFunctionLike(n)) {
          return false
        }
        else if (isClassLike(n)) {
          return forEach((<ClassLikeDeclaration>n).members, containsSuperCallAsComputedPropertyName)
        }
        return forEachChild(n, containsSuperCall)
      }

      def markThisReferencesAsErrors(n: Node): Unit {
        if (n.kind == SyntaxKind.ThisKeyword) {
          error(n, Diagnostics.this_cannot_be_referenced_in_current_location)
        }
        else if (n.kind != SyntaxKind.FunctionExpression && n.kind != SyntaxKind.FunctionDeclaration) {
          forEachChild(n, markThisReferencesAsErrors)
        }
      }

      def isInstancePropertyWithInitializer(n: Node): Boolean {
        return n.kind == SyntaxKind.PropertyDeclaration &&
          !(n.flags & NodeFlags.Static) &&
          !!(<PropertyDeclaration>n).initializer
      }

      // TS 1.0 spec (April 2014): 8.3.2
      // Constructors of classes with no extends clause may not contain super calls, whereas
      // constructors of derived classes must contain at least one super call somewhere in their def body.
      val containingClassDecl = <ClassDeclaration>node.parent
      if (getClassExtendsHeritageClauseElement(containingClassDecl)) {
        val containingClassSymbol = getSymbolOfNode(containingClassDecl)
        val containingClassInstanceType = <InterfaceType>getDeclaredTypeOfSymbol(containingClassSymbol)
        val baseConstructorType = getBaseConstructorTypeOfClass(containingClassInstanceType)

        if (containsSuperCall(node.body)) {
          if (baseConstructorType == nullType) {
            error(node, Diagnostics.A_constructor_cannot_contain_a_super_call_when_its_class_extends_null)
          }

          // The first statement in the body of a constructor (excluding prologue directives) must be a super call
          // if both of the following are true:
          // - The containing class is a derived class.
          // - The constructor declares parameter properties
          //   or the containing class declares instance member variables with initializers.
          val superCallShouldBeFirst =
            forEach((<ClassDeclaration>node.parent).members, isInstancePropertyWithInitializer) ||
            forEach(node.parameters, p => p.flags & (NodeFlags.Public | NodeFlags.Private | NodeFlags.Protected))

          // Skip past any prologue directives to find the first statement
          // to ensure that it was a super call.
          if (superCallShouldBeFirst) {
            val statements = (<Block>node.body).statements
            var superCallStatement: ExpressionStatement
            for (val statement of statements) {
              if (statement.kind == SyntaxKind.ExpressionStatement && isSuperCallExpression((<ExpressionStatement>statement).expression)) {
                superCallStatement = <ExpressionStatement>statement
                break
              }
              if (!isPrologueDirective(statement)) {
                break
              }
            }
            if (!superCallStatement) {
              error(node, Diagnostics.A_super_call_must_be_the_first_statement_in_the_constructor_when_a_class_contains_initialized_properties_or_has_parameter_properties)
            }
          }
        }
        else if (baseConstructorType != nullType) {
          error(node, Diagnostics.Constructors_for_derived_classes_must_contain_a_super_call)
        }
      }
    }

    def checkAccessorDeclaration(node: AccessorDeclaration) {
      if (produceDiagnostics) {
        // Grammar checking accessors
        checkGrammarFunctionLikeDeclaration(node) || checkGrammarAccessor(node) || checkGrammarComputedPropertyName(node.name)

        checkDecorators(node)
        checkSignatureDeclaration(node)
        if (node.kind == SyntaxKind.GetAccessor) {
          if (!isInAmbientContext(node) && nodeIsPresent(node.body) && (node.flags & NodeFlags.HasImplicitReturn)) {
            if (node.flags & NodeFlags.HasExplicitReturn) {
              if (compilerOptions.noImplicitReturns) {
                error(node.name, Diagnostics.Not_all_code_paths_return_a_value)
              }
            }
            else {
              error(node.name, Diagnostics.A_get_accessor_must_return_a_value)
            }
          }
        }
        // Do not use hasDynamicName here, because that returns false for well known symbols.
        // We want to perform checkComputedPropertyName for all computed properties, including
        // well known symbols.
        if (node.name.kind == SyntaxKind.ComputedPropertyName) {
          checkComputedPropertyName(<ComputedPropertyName>node.name)
        }
        if (!hasDynamicName(node)) {
          // TypeScript 1.0 spec (April 2014): 8.4.3
          // Accessors for the same member name must specify the same accessibility.
          val otherKind = node.kind == SyntaxKind.GetAccessor ? SyntaxKind.SetAccessor : SyntaxKind.GetAccessor
          val otherAccessor = <AccessorDeclaration>getDeclarationOfKind(node.symbol, otherKind)
          if (otherAccessor) {
            if (((node.flags & NodeFlags.AccessibilityModifier) != (otherAccessor.flags & NodeFlags.AccessibilityModifier))) {
              error(node.name, Diagnostics.Getter_and_setter_accessors_do_not_agree_in_visibility)
            }

            val currentAccessorType = getAnnotatedAccessorType(node)
            val otherAccessorType = getAnnotatedAccessorType(otherAccessor)
            // TypeScript 1.0 spec (April 2014): 4.5
            // If both accessors include type annotations, the specified types must be identical.
            if (currentAccessorType && otherAccessorType) {
              if (!isTypeIdenticalTo(currentAccessorType, otherAccessorType)) {
                error(node, Diagnostics.get_and_set_accessor_must_have_the_same_type)
              }
            }
          }
        }
        getTypeOfAccessors(getSymbolOfNode(node))
      }
      if (node.parent.kind != SyntaxKind.ObjectLiteralExpression) {
        checkSourceElement(node.body)
      }
      else {
        checkNodeDeferred(node)
      }
    }

    def checkAccessorDeferred(node: AccessorDeclaration) {
      checkSourceElement(node.body)
    }

    def checkMissingDeclaration(node: Node) {
      checkDecorators(node)
    }

    def checkTypeArgumentConstraints(typeParameters: TypeParameter[], typeArgumentNodes: TypeNode[]): Boolean {
      var typeArguments: Type[]
      var mapper: TypeMapper
      var result = true
      for (var i = 0; i < typeParameters.length; i++) {
        val constraint = getConstraintOfTypeParameter(typeParameters[i])
        if (constraint) {
          if (!typeArguments) {
            typeArguments = map(typeArgumentNodes, getTypeFromTypeNode)
            mapper = createTypeMapper(typeParameters, typeArguments)
          }
          val typeArgument = typeArguments[i]
          result = result && checkTypeAssignableTo(
            typeArgument,
            getTypeWithThisArgument(instantiateType(constraint, mapper), typeArgument),
            typeArgumentNodes[i],
            Diagnostics.Type_0_does_not_satisfy_the_constraint_1)
        }
      }
      return result
    }

    def checkTypeReferenceNode(node: TypeReferenceNode | ExpressionWithTypeArguments) {
      checkGrammarTypeArguments(node, node.typeArguments)
      val type = getTypeFromTypeReference(node)
      if (type != unknownType && node.typeArguments) {
        // Do type argument local checks only if referenced type is successfully resolved
        forEach(node.typeArguments, checkSourceElement)
        if (produceDiagnostics) {
          val symbol = getNodeLinks(node).resolvedSymbol
          val typeParameters = symbol.flags & SymbolFlags.TypeAlias ? getSymbolLinks(symbol).typeParameters : (<TypeReference>type).target.localTypeParameters
          checkTypeArgumentConstraints(typeParameters, node.typeArguments)
        }
      }
    }

    def checkTypeQuery(node: TypeQueryNode) {
      getTypeFromTypeQueryNode(node)
    }

    def checkTypeLiteral(node: TypeLiteralNode) {
      forEach(node.members, checkSourceElement)
      if (produceDiagnostics) {
        val type = getTypeFromTypeLiteralOrFunctionOrConstructorTypeNode(node)
        checkIndexConstraints(type)
        checkTypeForDuplicateIndexSignatures(node)
      }
    }

    def checkArrayType(node: ArrayTypeNode) {
      checkSourceElement(node.elementType)
    }

    def checkTupleType(node: TupleTypeNode) {
      // Grammar checking
      val hasErrorFromDisallowedTrailingComma = checkGrammarForDisallowedTrailingComma(node.elementTypes)
      if (!hasErrorFromDisallowedTrailingComma && node.elementTypes.length == 0) {
        grammarErrorOnNode(node, Diagnostics.A_tuple_type_element_list_cannot_be_empty)
      }

      forEach(node.elementTypes, checkSourceElement)
    }

    def checkUnionOrIntersectionType(node: UnionOrIntersectionTypeNode) {
      forEach(node.types, checkSourceElement)
    }

    def isPrivateWithinAmbient(node: Node): Boolean {
      return (node.flags & NodeFlags.Private) && isInAmbientContext(node)
    }

    def getEffectiveDeclarationFlags(n: Node, flagsToCheck: NodeFlags): NodeFlags {
      var flags = getCombinedNodeFlags(n)

      // children of classes (even ambient classes) should not be marked as ambient or export
      // because those flags have no useful semantics there.
      if (n.parent.kind != SyntaxKind.InterfaceDeclaration &&
        n.parent.kind != SyntaxKind.ClassDeclaration &&
        n.parent.kind != SyntaxKind.ClassExpression &&
        isInAmbientContext(n)) {
        if (!(flags & NodeFlags.Ambient)) {
          // It is nested in an ambient context, which means it is automatically exported
          flags |= NodeFlags.Export
        }
        flags |= NodeFlags.Ambient
      }

      return flags & flagsToCheck
    }

    def checkFunctionOrConstructorSymbol(symbol: Symbol): Unit {
      if (!produceDiagnostics) {
        return
      }

      def getCanonicalOverload(overloads: Declaration[], implementation: FunctionLikeDeclaration) {
        // Consider the canonical set of flags to be the flags of the bodyDeclaration or the first declaration
        // Error on all deviations from this canonical set of flags
        // The caveat is that if some overloads are defined in lib.d.ts, we don't want to
        // report the errors on those. To achieve this, we will say that the implementation is
        // the canonical signature only if it is in the same container as the first overload
        val implementationSharesContainerWithFirstOverload = implementation != () && implementation.parent == overloads[0].parent
        return implementationSharesContainerWithFirstOverload ? implementation : overloads[0]
      }

      def checkFlagAgreementBetweenOverloads(overloads: Declaration[], implementation: FunctionLikeDeclaration, flagsToCheck: NodeFlags, someOverloadFlags: NodeFlags, allOverloadFlags: NodeFlags): Unit {
        // Error if some overloads have a flag that is not shared by all overloads. To find the
        // deviations, we XOR someOverloadFlags with allOverloadFlags
        val someButNotAllOverloadFlags = someOverloadFlags ^ allOverloadFlags
        if (someButNotAllOverloadFlags != 0) {
          val canonicalFlags = getEffectiveDeclarationFlags(getCanonicalOverload(overloads, implementation), flagsToCheck)

          forEach(overloads, o => {
            val deviation = getEffectiveDeclarationFlags(o, flagsToCheck) ^ canonicalFlags
            if (deviation & NodeFlags.Export) {
              error(o.name, Diagnostics.Overload_signatures_must_all_be_exported_or_not_exported)
            }
            else if (deviation & NodeFlags.Ambient) {
              error(o.name, Diagnostics.Overload_signatures_must_all_be_ambient_or_non_ambient)
            }
            else if (deviation & (NodeFlags.Private | NodeFlags.Protected)) {
              error(o.name, Diagnostics.Overload_signatures_must_all_be_public_private_or_protected)
            }
            else if (deviation & NodeFlags.Abstract) {
              error(o.name, Diagnostics.Overload_signatures_must_all_be_abstract_or_not_abstract)
            }
          })
        }
      }

      def checkQuestionTokenAgreementBetweenOverloads(overloads: Declaration[], implementation: FunctionLikeDeclaration, someHaveQuestionToken: Boolean, allHaveQuestionToken: Boolean): Unit {
        if (someHaveQuestionToken != allHaveQuestionToken) {
          val canonicalHasQuestionToken = hasQuestionToken(getCanonicalOverload(overloads, implementation))
          forEach(overloads, o => {
            val deviation = hasQuestionToken(o) != canonicalHasQuestionToken
            if (deviation) {
              error(o.name, Diagnostics.Overload_signatures_must_all_be_optional_or_required)
            }
          })
        }
      }

      val flagsToCheck: NodeFlags = NodeFlags.Export | NodeFlags.Ambient | NodeFlags.Private | NodeFlags.Protected | NodeFlags.Abstract
      var someNodeFlags: NodeFlags = 0
      var allNodeFlags = flagsToCheck
      var someHaveQuestionToken = false
      var allHaveQuestionToken = true
      var hasOverloads = false
      var bodyDeclaration: FunctionLikeDeclaration
      var lastSeenNonAmbientDeclaration: FunctionLikeDeclaration
      var previousDeclaration: FunctionLikeDeclaration

      val declarations = symbol.declarations
      val isConstructor = (symbol.flags & SymbolFlags.Constructor) != 0

      def reportImplementationExpectedError(node: FunctionLikeDeclaration): Unit {
        if (node.name && nodeIsMissing(node.name)) {
          return
        }

        var seen = false
        val subsequentNode = forEachChild(node.parent, c => {
          if (seen) {
            return c
          }
          else {
            seen = c == node
          }
        })
        // We may be here because of some extra junk between overloads that could not be parsed into a valid node.
        // In this case the subsequent node is not really consecutive (.pos != node.end), and we must ignore it here.
        if (subsequentNode && subsequentNode.pos == node.end) {
          if (subsequentNode.kind == node.kind) {
            val errorNode: Node = (<FunctionLikeDeclaration>subsequentNode).name || subsequentNode
            // TODO(jfreeman): These are methods, so handle computed name case
            if (node.name && (<FunctionLikeDeclaration>subsequentNode).name && (<Identifier>node.name).text == (<Identifier>(<FunctionLikeDeclaration>subsequentNode).name).text) {
              val reportError =
                (node.kind == SyntaxKind.MethodDeclaration || node.kind == SyntaxKind.MethodSignature) &&
                (node.flags & NodeFlags.Static) != (subsequentNode.flags & NodeFlags.Static)
              // we can get here in two cases
              // 1. mixed static and instance class members
              // 2. something with the same name was defined before the set of overloads that prevents them from merging
              // here we'll report error only for the first case since for second we should already report error in binder
              if (reportError) {
                val diagnostic = node.flags & NodeFlags.Static ? Diagnostics.Function_overload_must_be_static : Diagnostics.Function_overload_must_not_be_static
                error(errorNode, diagnostic)
              }
              return
            }
            else if (nodeIsPresent((<FunctionLikeDeclaration>subsequentNode).body)) {
              error(errorNode, Diagnostics.Function_implementation_name_must_be_0, declarationNameToString(node.name))
              return
            }
          }
        }
        val errorNode: Node = node.name || node
        if (isConstructor) {
          error(errorNode, Diagnostics.Constructor_implementation_is_missing)
        }
        else {
          // Report different errors regarding non-consecutive blocks of declarations depending on whether
          // the node in question is abstract.
          if (node.flags & NodeFlags.Abstract) {
            error(errorNode, Diagnostics.All_declarations_of_an_abstract_method_must_be_consecutive)
          }
          else {
            error(errorNode, Diagnostics.Function_implementation_is_missing_or_not_immediately_following_the_declaration)
          }
        }
      }

      // when checking exported def declarations across modules check only duplicate implementations
      // names and consistency of modifiers are verified when we check local symbol
      val isExportSymbolInsideModule = symbol.parent && symbol.parent.flags & SymbolFlags.Module
      var duplicateFunctionDeclaration = false
      var multipleConstructorImplementation = false
      for (val current of declarations) {
        val node = <FunctionLikeDeclaration>current
        val inAmbientContext = isInAmbientContext(node)
        val inAmbientContextOrInterface = node.parent.kind == SyntaxKind.InterfaceDeclaration || node.parent.kind == SyntaxKind.TypeLiteral || inAmbientContext
        if (inAmbientContextOrInterface) {
          // check if declarations are consecutive only if they are non-ambient
          // 1. ambient declarations can be interleaved
          // i.e. this is legal
          //   declare def foo()
          //   declare def bar()
          //   declare def foo()
          // 2. mixing ambient and non-ambient declarations is a separate error that will be reported - do not want to report an extra one
          previousDeclaration = ()
        }

        if (node.kind == SyntaxKind.FunctionDeclaration || node.kind == SyntaxKind.MethodDeclaration || node.kind == SyntaxKind.MethodSignature || node.kind == SyntaxKind.Constructor) {
          val currentNodeFlags = getEffectiveDeclarationFlags(node, flagsToCheck)
          someNodeFlags |= currentNodeFlags
          allNodeFlags &= currentNodeFlags
          someHaveQuestionToken = someHaveQuestionToken || hasQuestionToken(node)
          allHaveQuestionToken = allHaveQuestionToken && hasQuestionToken(node)

          if (nodeIsPresent(node.body) && bodyDeclaration) {
            if (isConstructor) {
              multipleConstructorImplementation = true
            }
            else {
              duplicateFunctionDeclaration = true
            }
          }
          else if (!isExportSymbolInsideModule && previousDeclaration && previousDeclaration.parent == node.parent && previousDeclaration.end != node.pos) {
            reportImplementationExpectedError(previousDeclaration)
          }

          if (nodeIsPresent(node.body)) {
            if (!bodyDeclaration) {
              bodyDeclaration = node
            }
          }
          else {
            hasOverloads = true
          }

          previousDeclaration = node

          if (!inAmbientContextOrInterface) {
            lastSeenNonAmbientDeclaration = node
          }
        }
      }

      if (multipleConstructorImplementation) {
        forEach(declarations, declaration => {
          error(declaration, Diagnostics.Multiple_constructor_implementations_are_not_allowed)
        })
      }

      if (duplicateFunctionDeclaration) {
        forEach(declarations, declaration => {
          error(declaration.name, Diagnostics.Duplicate_function_implementation)
        })
      }

      // Abstract methods can't have an implementation -- in particular, they don't need one.
      if (!isExportSymbolInsideModule && lastSeenNonAmbientDeclaration && !lastSeenNonAmbientDeclaration.body &&
        !(lastSeenNonAmbientDeclaration.flags & NodeFlags.Abstract)) {
        reportImplementationExpectedError(lastSeenNonAmbientDeclaration)
      }

      if (hasOverloads) {
        checkFlagAgreementBetweenOverloads(declarations, bodyDeclaration, flagsToCheck, someNodeFlags, allNodeFlags)
        checkQuestionTokenAgreementBetweenOverloads(declarations, bodyDeclaration, someHaveQuestionToken, allHaveQuestionToken)

        if (bodyDeclaration) {
          val signatures = getSignaturesOfSymbol(symbol)
          val bodySignature = getSignatureFromDeclaration(bodyDeclaration)
          for (val signature of signatures) {
            if (!isImplementationCompatibleWithOverload(bodySignature, signature)) {
              error(signature.declaration, Diagnostics.Overload_signature_is_not_compatible_with_function_implementation)
              break
            }
          }
        }
      }
    }

    def checkExportsOnMergedDeclarations(node: Node): Unit {
      if (!produceDiagnostics) {
        return
      }

      // if localSymbol is defined on node then node itself is exported - check is required
      var symbol = node.localSymbol
      if (!symbol) {
        // local symbol is () => this declaration is non-exported.
        // however symbol might contain other declarations that are exported
        symbol = getSymbolOfNode(node)
        if (!(symbol.flags & SymbolFlags.Export)) {
          // this is a pure local symbol (all declarations are non-exported) - no need to check anything
          return
        }
      }

      // run the check only for the first declaration in the list
      if (getDeclarationOfKind(symbol, node.kind) != node) {
        return
      }

      // we use SymbolFlags.ExportValue, SymbolFlags.ExportType and SymbolFlags.ExportNamespace
      // to denote disjoint declarationSpaces (without making new enum type).
      var exportedDeclarationSpaces = SymbolFlags.None
      var nonExportedDeclarationSpaces = SymbolFlags.None
      var defaultExportedDeclarationSpaces = SymbolFlags.None
      for (val d of symbol.declarations) {
        val declarationSpaces = getDeclarationSpaces(d)
        val effectiveDeclarationFlags = getEffectiveDeclarationFlags(d, NodeFlags.Export | NodeFlags.Default)

        if (effectiveDeclarationFlags & NodeFlags.Export) {
          if (effectiveDeclarationFlags & NodeFlags.Default) {
            defaultExportedDeclarationSpaces |= declarationSpaces
          }
          else {
            exportedDeclarationSpaces |= declarationSpaces
          }
        }
        else {
          nonExportedDeclarationSpaces |= declarationSpaces
        }
      }

      // Spaces for anything not declared a 'default export'.
      val nonDefaultExportedDeclarationSpaces = exportedDeclarationSpaces | nonExportedDeclarationSpaces

      val commonDeclarationSpacesForExportsAndLocals = exportedDeclarationSpaces & nonExportedDeclarationSpaces
      val commonDeclarationSpacesForDefaultAndNonDefault = defaultExportedDeclarationSpaces & nonDefaultExportedDeclarationSpaces

      if (commonDeclarationSpacesForExportsAndLocals || commonDeclarationSpacesForDefaultAndNonDefault) {
        // declaration spaces for exported and non-exported declarations intersect
        for (val d of symbol.declarations) {
          val declarationSpaces = getDeclarationSpaces(d)

          // Only error on the declarations that contributed to the intersecting spaces.
          if (declarationSpaces & commonDeclarationSpacesForDefaultAndNonDefault) {
            error(d.name, Diagnostics.Merged_declaration_0_cannot_include_a_default_export_declaration_Consider_adding_a_separate_export_default_0_declaration_instead, declarationNameToString(d.name))
          }
          else if (declarationSpaces & commonDeclarationSpacesForExportsAndLocals) {
            error(d.name, Diagnostics.Individual_declarations_in_merged_declaration_0_must_be_all_exported_or_all_local, declarationNameToString(d.name))
          }
        }
      }

      def getDeclarationSpaces(d: Declaration): SymbolFlags {
        switch (d.kind) {
          case SyntaxKind.InterfaceDeclaration:
            return SymbolFlags.ExportType
          case SyntaxKind.ModuleDeclaration:
            return isAmbientModule(d) || getModuleInstanceState(d) != ModuleInstanceState.NonInstantiated
              ? SymbolFlags.ExportNamespace | SymbolFlags.ExportValue
              : SymbolFlags.ExportNamespace
          case SyntaxKind.ClassDeclaration:
          case SyntaxKind.EnumDeclaration:
            return SymbolFlags.ExportType | SymbolFlags.ExportValue
          case SyntaxKind.ImportEqualsDeclaration:
            var result: SymbolFlags = 0
            val target = resolveAlias(getSymbolOfNode(d))
            forEach(target.declarations, d => { result |= getDeclarationSpaces(d); })
            return result
          default:
            return SymbolFlags.ExportValue
        }
      }
    }

    def checkNonThenableType(type: Type, location?: Node, message?: DiagnosticMessage) {
      type = getWidenedType(type)
      if (!isTypeAny(type) && isTypeAssignableTo(type, getGlobalThenableType())) {
        if (location) {
          if (!message) {
            message = Diagnostics.Operand_for_await_does_not_have_a_valid_callable_then_member
          }

          error(location, message)
        }

        return unknownType
      }

      return type
    }

    /**
      * Gets the "promised type" of a promise.
      * @param type The type of the promise.
      * @remarks The "promised type" of a type is the type of the "value" parameter of the "onfulfilled" callback.
      */
    def getPromisedType(promise: Type): Type {
      //
      //  { // promise
      //    then( // thenFunction
      //      onfulfilled: ( // onfulfilledParameterType
      //        value: T // valueParameterType
      //      ) => any
      //    ): any
      //  }
      //

      if (promise.flags & TypeFlags.Any) {
        return ()
      }

      if ((promise.flags & TypeFlags.Reference) && (<GenericType>promise).target == tryGetGlobalPromiseType()) {
        return (<GenericType>promise).typeArguments[0]
      }

      val globalPromiseLikeType = getInstantiatedGlobalPromiseLikeType()
      if (globalPromiseLikeType == emptyObjectType || !isTypeAssignableTo(promise, globalPromiseLikeType)) {
        return ()
      }

      val thenFunction = getTypeOfPropertyOfType(promise, "then")
      if (thenFunction && (thenFunction.flags & TypeFlags.Any)) {
        return ()
      }

      val thenSignatures = thenFunction ? getSignaturesOfType(thenFunction, SignatureKind.Call) : emptyArray
      if (thenSignatures.length == 0) {
        return ()
      }

      val onfulfilledParameterType = getUnionType(map(thenSignatures, getTypeOfFirstParameterOfSignature))
      if (onfulfilledParameterType.flags & TypeFlags.Any) {
        return ()
      }

      val onfulfilledParameterSignatures = getSignaturesOfType(onfulfilledParameterType, SignatureKind.Call)
      if (onfulfilledParameterSignatures.length == 0) {
        return ()
      }

      val valueParameterType = getUnionType(map(onfulfilledParameterSignatures, getTypeOfFirstParameterOfSignature))
      return valueParameterType
    }

    def getTypeOfFirstParameterOfSignature(signature: Signature) {
      return getTypeAtPosition(signature, 0)
    }

    /**
      * Gets the "awaited type" of a type.
      * @param type The type to await.
      * @remarks The "awaited type" of an expression is its "promised type" if the expression is a
      * Promise-like type; otherwise, it is the type of the expression. This is used to reflect
      * The runtime behavior of the `await` keyword.
      */
    def getAwaitedType(type: Type) {
      return checkAwaitedType(type, /*location*/ (), /*message*/ ())
    }

    def checkAwaitedType(type: Type, location?: Node, message?: DiagnosticMessage) {
      return checkAwaitedTypeWorker(type)

      def checkAwaitedTypeWorker(type: Type): Type {
        if (type.flags & TypeFlags.Union) {
          val types: Type[] = []
          for (val constituentType of (<UnionType>type).types) {
            types.push(checkAwaitedTypeWorker(constituentType))
          }

          return getUnionType(types)
        }
        else {
          val promisedType = getPromisedType(type)
          if (promisedType == ()) {
            // The type was not a PromiseLike, so it could not be unwrapped any further.
            // As long as the type does not have a callable "then" property, it is
            // safe to return the type; otherwise, an error will have been reported in
            // the call to checkNonThenableType and we will return unknownType.
            //
            // An example of a non-promise "thenable" might be:
            //
            //  await { then(): Unit {} }
            //
            // The "thenable" does not match the minimal definition for a PromiseLike. When
            // a Promise/A+-compatible or ES6 promise tries to adopt this value, the promise
            // will never settle. We treat this as an error to help flag an early indicator
            // of a runtime problem. If the user wants to return this value from an async
            // def, they would need to wrap it in some other value. If they want it to
            // be treated as a promise, they can cast to <any>.
            return checkNonThenableType(type, location, message)
          }
          else {
            if (type.id == promisedType.id || awaitedTypeStack.indexOf(promisedType.id) >= 0) {
              // We have a bad actor in the form of a promise whose promised type is
              // the same promise type, or a mutually recursive promise. Return the
              // unknown type as we cannot guess the shape. If this were the actual
              // case in the JavaScript, this Promise would never resolve.
              //
              // An example of a bad actor with a singly-recursive promise type might
              // be:
              //
              //  trait BadPromise {
              //    then(
              //      onfulfilled: (value: BadPromise) => any,
              //      onrejected: (error: any) => any): BadPromise
              //  }
              //
              // The above trait will pass the PromiseLike check, and return a
              // promised type of `BadPromise`. Since this is a self reference, we
              // don't want to keep recursing ad infinitum.
              //
              // An example of a bad actor in the form of a mutually-recursive
              // promise type might be:
              //
              //  trait BadPromiseA {
              //    then(
              //      onfulfilled: (value: BadPromiseB) => any,
              //      onrejected: (error: any) => any): BadPromiseB
              //  }
              //
              //  trait BadPromiseB {
              //    then(
              //      onfulfilled: (value: BadPromiseA) => any,
              //      onrejected: (error: any) => any): BadPromiseA
              //  }
              //
              if (location) {
                error(
                  location,
                  Diagnostics._0_is_referenced_directly_or_indirectly_in_the_fulfillment_callback_of_its_own_then_method,
                  symbolToString(type.symbol))
              }

              return unknownType
            }

            // Keep track of the type we're about to unwrap to avoid bad recursive promise types.
            // See the comments above for more information.
            awaitedTypeStack.push(type.id)
            val awaitedType = checkAwaitedTypeWorker(promisedType)
            awaitedTypeStack.pop()
            return awaitedType
          }
        }
      }
    }

    /**
     * Checks that the return type provided is an instantiation of the global Promise<T> type
     * and returns the awaited type of the return type.
     *
     * @param returnType The return type of a FunctionLikeDeclaration
     * @param location The node on which to report the error.
     */
    def checkCorrectPromiseType(returnType: Type, location: Node) {
      if (returnType == unknownType) {
        // The return type already had some other error, so we ignore and return
        // the unknown type.
        return unknownType
      }

      val globalPromiseType = getGlobalPromiseType()
      if (globalPromiseType == emptyGenericType
        || globalPromiseType == getTargetType(returnType)) {
        // Either we couldn't resolve the global promise type, which would have already
        // reported an error, or we could resolve it and the return type is a valid type
        // reference to the global type. In either case, we return the awaited type for
        // the return type.
        return checkAwaitedType(returnType, location, Diagnostics.An_async_function_or_method_must_have_a_valid_awaitable_return_type)
      }

      // The promise type was not a valid type reference to the global promise type, so we
      // report an error and return the unknown type.
      error(location, Diagnostics.The_return_type_of_an_async_function_or_method_must_be_the_global_Promise_T_type)
      return unknownType
    }

    /**
      * Checks the return type of an async def to ensure it is a compatible
      * Promise implementation.
      * @param node The signature to check
      * @param returnType The return type for the def
      * @remarks
      * This checks that an async def has a valid Promise-compatible return type,
      * and returns the *awaited type* of the promise. An async def has a valid
      * Promise-compatible return type if the resolved value of the return type has a
      * construct signature that takes in an `initializer` def that in turn supplies
      * a `resolve` def as one of its arguments and results in an object with a
      * callable `then` signature.
      */
    def checkAsyncFunctionReturnType(node: FunctionLikeDeclaration): Type {
      if (languageVersion >= ScriptTarget.ES6) {
        val returnType = getTypeFromTypeNode(node.type)
        return checkCorrectPromiseType(returnType, node.type)
      }

      val globalPromiseConstructorLikeType = getGlobalPromiseConstructorLikeType()
      if (globalPromiseConstructorLikeType == emptyObjectType) {
        // If we couldn't resolve the global PromiseConstructorLike type we cannot verify
        // compatibility with __awaiter.
        return unknownType
      }

      // As part of our emit for an async def, we will need to emit the entity name of
      // the return type annotation as an expression. To meet the necessary runtime semantics
      // for __awaiter, we must also check that the type of the declaration (e.g. the static
      // side or "constructor" of the promise type) is compatible `PromiseConstructorLike`.
      //
      // An example might be (from lib.es6.d.ts):
      //
      //  trait Promise<T> { ... }
      //  trait PromiseConstructor {
      //    new <T>(...): Promise<T>
      //  }
      //  declare var Promise: PromiseConstructor
      //
      // When an async def declares a return type annotation of `Promise<T>`, we
      // need to get the type of the `Promise` variable declaration above, which would
      // be `PromiseConstructor`.
      //
      // The same case applies to a class:
      //
      //  declare class Promise<T> {
      //    constructor(...)
      //    then<U>(...): Promise<U>
      //  }
      //
      // When we get the type of the `Promise` symbol here, we get the type of the static
      // side of the `Promise` class, which would be `{ new <T>(...): Promise<T> }`.

      val promiseType = getTypeFromTypeNode(node.type)
      if (promiseType == unknownType && compilerOptions.isolatedModules) {
        // If we are compiling with isolatedModules, we may not be able to resolve the
        // type as a value. As such, we will just return unknownType
        return unknownType
      }

      val promiseConstructor = getNodeLinks(node.type).resolvedSymbol
      if (!promiseConstructor || !symbolIsValue(promiseConstructor)) {
        val typeName = promiseConstructor
          ? symbolToString(promiseConstructor)
          : typeToString(promiseType)
        error(node, Diagnostics.Type_0_is_not_a_valid_async_function_return_type, typeName)
        return unknownType
      }

      // If the Promise constructor, resolved locally, is an alias symbol we should mark it as referenced.
      checkReturnTypeAnnotationAsExpression(node)

      // Validate the promise constructor type.
      val promiseConstructorType = getTypeOfSymbol(promiseConstructor)
      if (!checkTypeAssignableTo(promiseConstructorType, globalPromiseConstructorLikeType, node, Diagnostics.Type_0_is_not_a_valid_async_function_return_type)) {
        return unknownType
      }

      // Verify there is no local declaration that could collide with the promise constructor.
      val promiseName = getEntityNameFromTypeNode(node.type)
      val promiseNameOrNamespaceRoot = getFirstIdentifier(promiseName)
      val rootSymbol = getSymbol(node.locals, promiseNameOrNamespaceRoot.text, SymbolFlags.Value)
      if (rootSymbol) {
        error(rootSymbol.valueDeclaration, Diagnostics.Duplicate_identifier_0_Compiler_uses_declaration_1_to_support_async_functions,
          promiseNameOrNamespaceRoot.text,
          getFullyQualifiedName(promiseConstructor))
        return unknownType
      }

      // Get and return the awaited type of the return type.
      return checkAwaitedType(promiseType, node, Diagnostics.An_async_function_or_method_must_have_a_valid_awaitable_return_type)
    }

    /** Check a decorator */
    def checkDecorator(node: Decorator): Unit {
      val signature = getResolvedSignature(node)
      val returnType = getReturnTypeOfSignature(signature)
      if (returnType.flags & TypeFlags.Any) {
        return
      }

      var expectedReturnType: Type
      val headMessage = getDiagnosticHeadMessageForDecoratorResolution(node)
      var errorInfo: DiagnosticMessageChain
      switch (node.parent.kind) {
        case SyntaxKind.ClassDeclaration:
          val classSymbol = getSymbolOfNode(node.parent)
          val classConstructorType = getTypeOfSymbol(classSymbol)
          expectedReturnType = getUnionType([classConstructorType, voidType])
          break

        case SyntaxKind.Parameter:
          expectedReturnType = voidType
          errorInfo = chainDiagnosticMessages(
            errorInfo,
            Diagnostics.The_return_type_of_a_parameter_decorator_function_must_be_either_void_or_any)

          break

        case SyntaxKind.PropertyDeclaration:
          expectedReturnType = voidType
          errorInfo = chainDiagnosticMessages(
            errorInfo,
            Diagnostics.The_return_type_of_a_property_decorator_function_must_be_either_void_or_any)
          break

        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.GetAccessor:
        case SyntaxKind.SetAccessor:
          val methodType = getTypeOfNode(node.parent)
          val descriptorType = createTypedPropertyDescriptorType(methodType)
          expectedReturnType = getUnionType([descriptorType, voidType])
          break
      }

      checkTypeAssignableTo(
        returnType,
        expectedReturnType,
        node,
        headMessage,
        errorInfo)
    }

    /** Checks a type reference node as an expression. */
    def checkTypeNodeAsExpression(node: TypeNode) {
      // When we are emitting type metadata for decorators, we need to try to check the type
      // as if it were an expression so that we can emit the type in a value position when we
      // serialize the type metadata.
      if (node && node.kind == SyntaxKind.TypeReference) {
        val root = getFirstIdentifier((<TypeReferenceNode>node).typeName)
        val meaning = root.parent.kind == SyntaxKind.TypeReference ? SymbolFlags.Type : SymbolFlags.Namespace
        // Resolve type so we know which symbol is referenced
        val rootSymbol = resolveName(root, root.text, meaning | SymbolFlags.Alias, /*nameNotFoundMessage*/ (), /*nameArg*/ ())
        // Resolved symbol is alias
        if (rootSymbol && rootSymbol.flags & SymbolFlags.Alias) {
          val aliasTarget = resolveAlias(rootSymbol)
          // If alias has value symbol - mark alias as referenced
          if (aliasTarget.flags & SymbolFlags.Value && !isConstEnumOrConstEnumOnlyModule(resolveAlias(rootSymbol))) {
            markAliasSymbolAsReferenced(rootSymbol)
          }
        }
      }
    }

    /**
      * Checks the type annotation of an accessor declaration or property declaration as
      * an expression if it is a type reference to a type with a value declaration.
      */
    def checkTypeAnnotationAsExpression(node: VariableLikeDeclaration) {
      checkTypeNodeAsExpression((<PropertyDeclaration>node).type)
    }

    def checkReturnTypeAnnotationAsExpression(node: FunctionLikeDeclaration) {
      checkTypeNodeAsExpression(node.type)
    }

    /** Checks the type annotation of the parameters of a def/method or the constructor of a class as expressions */
    def checkParameterTypeAnnotationsAsExpressions(node: FunctionLikeDeclaration) {
      // ensure all type annotations with a value declaration are checked as an expression
      for (val parameter of node.parameters) {
        checkTypeAnnotationAsExpression(parameter)
      }
    }

    /** Check the decorators of a node */
    def checkDecorators(node: Node): Unit {
      if (!node.decorators) {
        return
      }

      // skip this check for nodes that cannot have decorators. These should have already had an error reported by
      // checkGrammarDecorators.
      if (!nodeCanBeDecorated(node)) {
        return
      }

      if (!compilerOptions.experimentalDecorators) {
        error(node, Diagnostics.Experimental_support_for_decorators_is_a_feature_that_is_subject_to_change_in_a_future_release_Specify_experimentalDecorators_to_remove_this_warning)
      }

      if (compilerOptions.emitDecoratorMetadata) {
        // we only need to perform these checks if we are emitting serialized type metadata for the target of a decorator.
        switch (node.kind) {
          case SyntaxKind.ClassDeclaration:
            val constructor = getFirstConstructorWithBody(<ClassDeclaration>node)
            if (constructor) {
              checkParameterTypeAnnotationsAsExpressions(constructor)
            }
            break

          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.GetAccessor:
          case SyntaxKind.SetAccessor:
            checkParameterTypeAnnotationsAsExpressions(<FunctionLikeDeclaration>node)
            checkReturnTypeAnnotationAsExpression(<FunctionLikeDeclaration>node)
            break

          case SyntaxKind.PropertyDeclaration:
          case SyntaxKind.Parameter:
            checkTypeAnnotationAsExpression(<PropertyDeclaration | ParameterDeclaration>node)
            break
        }
      }

      forEach(node.decorators, checkDecorator)
    }

    def checkFunctionDeclaration(node: FunctionDeclaration): Unit {
      if (produceDiagnostics) {
        checkFunctionOrMethodDeclaration(node) || checkGrammarForGenerator(node)

        checkCollisionWithCapturedSuperVariable(node, node.name)
        checkCollisionWithCapturedThisVariable(node, node.name)
        checkCollisionWithRequireExportsInGeneratedCode(node, node.name)
        checkCollisionWithGlobalPromiseInGeneratedCode(node, node.name)
      }
    }

    def checkFunctionOrMethodDeclaration(node: FunctionDeclaration | MethodDeclaration): Unit {
      checkDecorators(node)
      checkSignatureDeclaration(node)
      val isAsync = isAsyncFunctionLike(node)

      // Do not use hasDynamicName here, because that returns false for well known symbols.
      // We want to perform checkComputedPropertyName for all computed properties, including
      // well known symbols.
      if (node.name && node.name.kind == SyntaxKind.ComputedPropertyName) {
        // This check will account for methods in class/trait declarations,
        // as well as accessors in classes/object literals
        checkComputedPropertyName(<ComputedPropertyName>node.name)
      }

      if (!hasDynamicName(node)) {
        // first we want to check the local symbol that contain this declaration
        // - if node.localSymbol != () - this is current declaration is exported and localSymbol points to the local symbol
        // - if node.localSymbol == () - this node is non-exported so we can just pick the result of getSymbolOfNode
        val symbol = getSymbolOfNode(node)
        val localSymbol = node.localSymbol || symbol

        // Since the javascript won't do semantic analysis like typescript,
        // if the javascript file comes before the typescript file and both contain same name functions,
        // checkFunctionOrConstructorSymbol wouldn't be called if we didnt ignore javascript def.
        val firstDeclaration = forEach(localSymbol.declarations,
          // Get first non javascript def declaration
          declaration => declaration.kind == node.kind && !isSourceFileJavaScript(getSourceFileOfNode(declaration)) ?
            declaration : ())

        // Only type check the symbol once
        if (node == firstDeclaration) {
          checkFunctionOrConstructorSymbol(localSymbol)
        }

        if (symbol.parent) {
          // run check once for the first declaration
          if (getDeclarationOfKind(symbol, node.kind) == node) {
            // run check on symbol to check that modifiers agree across all exported declarations
            checkFunctionOrConstructorSymbol(symbol)
          }
        }
      }

      checkSourceElement(node.body)
      if (!node.asteriskToken) {
        val returnOrPromisedType = node.type && (isAsync ? checkAsyncFunctionReturnType(node) : getTypeFromTypeNode(node.type))
        checkAllCodePathsInNonVoidFunctionReturnOrThrow(node, returnOrPromisedType)
      }

      if (produceDiagnostics && !node.type) {
        // Report an implicit any error if there is no body, no explicit return type, and node is not a private method
        // in an ambient context
        if (compilerOptions.noImplicitAny && nodeIsMissing(node.body) && !isPrivateWithinAmbient(node)) {
          reportImplicitAnyError(node, anyType)
        }

        if (node.asteriskToken && nodeIsPresent(node.body)) {
          // A generator with a body and no type annotation can still cause errors. It can error if the
          // yielded values have no common supertype, or it can give an implicit any error if it has no
          // yielded values. The only way to trigger these errors is to try checking its return type.
          getReturnTypeOfSignature(getSignatureFromDeclaration(node))
        }
      }
    }

    def checkBlock(node: Block) {
      // Grammar checking for SyntaxKind.Block
      if (node.kind == SyntaxKind.Block) {
        checkGrammarStatementInAmbientContext(node)
      }
      forEach(node.statements, checkSourceElement)
    }

    def checkCollisionWithArgumentsInGeneratedCode(node: SignatureDeclaration) {
      // no rest parameters \ declaration context \ overload - no codegen impact
      if (!hasRestParameter(node) || isInAmbientContext(node) || nodeIsMissing((<FunctionLikeDeclaration>node).body)) {
        return
      }

      forEach(node.parameters, p => {
        if (p.name && !isBindingPattern(p.name) && (<Identifier>p.name).text == argumentsSymbol.name) {
          error(p, Diagnostics.Duplicate_identifier_arguments_Compiler_uses_arguments_to_initialize_rest_parameters)
        }
      })
    }

    def needCollisionCheckForIdentifier(node: Node, identifier: Identifier, name: String): Boolean {
      if (!(identifier && identifier.text == name)) {
        return false
      }

      if (node.kind == SyntaxKind.PropertyDeclaration ||
        node.kind == SyntaxKind.PropertySignature ||
        node.kind == SyntaxKind.MethodDeclaration ||
        node.kind == SyntaxKind.MethodSignature ||
        node.kind == SyntaxKind.GetAccessor ||
        node.kind == SyntaxKind.SetAccessor) {
        // it is ok to have member named '_super' or '_this' - member access is always qualified
        return false
      }

      if (isInAmbientContext(node)) {
        // ambient context - no codegen impact
        return false
      }

      val root = getRootDeclaration(node)
      if (root.kind == SyntaxKind.Parameter && nodeIsMissing((<FunctionLikeDeclaration>root.parent).body)) {
        // just an overload - no codegen impact
        return false
      }

      return true
    }

    def checkCollisionWithCapturedThisVariable(node: Node, name: Identifier): Unit {
      if (needCollisionCheckForIdentifier(node, name, "_this")) {
        potentialThisCollisions.push(node)
      }
    }

    // this def will run after checking the source file so 'CaptureThis' is correct for all nodes
    def checkIfThisIsCapturedInEnclosingScope(node: Node): Unit {
      var current = node
      while (current) {
        if (getNodeCheckFlags(current) & NodeCheckFlags.CaptureThis) {
          val isDeclaration = node.kind != SyntaxKind.Identifier
          if (isDeclaration) {
            error((<Declaration>node).name, Diagnostics.Duplicate_identifier_this_Compiler_uses_variable_declaration_this_to_capture_this_reference)
          }
          else {
            error(node, Diagnostics.Expression_resolves_to_variable_declaration_this_that_compiler_uses_to_capture_this_reference)
          }
          return
        }
        current = current.parent
      }
    }

    def checkCollisionWithCapturedSuperVariable(node: Node, name: Identifier) {
      if (!needCollisionCheckForIdentifier(node, name, "_super")) {
        return
      }

      // bubble up and find containing type
      val enclosingClass = getContainingClass(node)
      // if containing type was not found or it is ambient - exit (no codegen)
      if (!enclosingClass || isInAmbientContext(enclosingClass)) {
        return
      }

      if (getClassExtendsHeritageClauseElement(enclosingClass)) {
        val isDeclaration = node.kind != SyntaxKind.Identifier
        if (isDeclaration) {
          error(node, Diagnostics.Duplicate_identifier_super_Compiler_uses_super_to_capture_base_class_reference)
        }
        else {
          error(node, Diagnostics.Expression_resolves_to_super_that_compiler_uses_to_capture_base_class_reference)
        }
      }
    }

    def checkCollisionWithRequireExportsInGeneratedCode(node: Node, name: Identifier) {
      if (!needCollisionCheckForIdentifier(node, name, "require") && !needCollisionCheckForIdentifier(node, name, "exports")) {
        return
      }

      // Uninstantiated modules shouldnt do this check
      if (node.kind == SyntaxKind.ModuleDeclaration && getModuleInstanceState(node) != ModuleInstanceState.Instantiated) {
        return
      }

      // In case of variable declaration, node.parent is variable statement so look at the variable statement's parent
      val parent = getDeclarationContainer(node)
      if (parent.kind == SyntaxKind.SourceFile && isExternalOrCommonJsModule(<SourceFile>parent)) {
        // If the declaration happens to be in external module, report error that require and exports are reserved keywords
        error(name, Diagnostics.Duplicate_identifier_0_Compiler_reserves_name_1_in_top_level_scope_of_a_module,
          declarationNameToString(name), declarationNameToString(name))
      }
    }

    def checkCollisionWithGlobalPromiseInGeneratedCode(node: Node, name: Identifier): Unit {
      if (!needCollisionCheckForIdentifier(node, name, "Promise")) {
        return
      }

      // Uninstantiated modules shouldnt do this check
      if (node.kind == SyntaxKind.ModuleDeclaration && getModuleInstanceState(node) != ModuleInstanceState.Instantiated) {
        return
      }

      // In case of variable declaration, node.parent is variable statement so look at the variable statement's parent
      val parent = getDeclarationContainer(node)
      if (parent.kind == SyntaxKind.SourceFile && isExternalOrCommonJsModule(<SourceFile>parent) && parent.flags & NodeFlags.HasAsyncFunctions) {
        // If the declaration happens to be in external module, report error that Promise is a reserved identifier.
        error(name, Diagnostics.Duplicate_identifier_0_Compiler_reserves_name_1_in_top_level_scope_of_a_module_containing_async_functions,
          declarationNameToString(name), declarationNameToString(name))
      }
    }

    def checkVarDeclaredNamesNotShadowed(node: VariableDeclaration | BindingElement) {
      // - ScriptBody : StatementList
      // It is a Syntax Error if any element of the LexicallyDeclaredNames of StatementList
      // also occurs in the VarDeclaredNames of StatementList.

      // - Block : { StatementList }
      // It is a Syntax Error if any element of the LexicallyDeclaredNames of StatementList
      // also occurs in the VarDeclaredNames of StatementList.

      // Variable declarations are hoisted to the top of their def scope. They can shadow
      // block scoped declarations, which bind tighter. this will not be flagged as duplicate definition
      // by the binder as the declaration scope is different.
      // A non-initialized declaration is a no-op as the block declaration will resolve before the var
      // declaration. the problem is if the declaration has an initializer. this will act as a write to the
      // block declared value. this is fine for var, but not val.
      // Only consider declarations with initializers, uninitialized val declarations will not
      // step on a var/val variable.
      // Do not consider val and val declarations, as duplicate block-scoped declarations
      // are handled by the binder.
      // We are only looking for val declarations that step on var\val declarations from a
      // different scope. e.g.:
      //    {
      //      val x = 0; // localDeclarationSymbol obtained after name resolution will correspond to this declaration
      //      val x = 0; // symbol for this declaration will be 'symbol'
      //    }

      // skip block-scoped variables and parameters
      if ((getCombinedNodeFlags(node) & NodeFlags.BlockScoped) != 0 || isParameterDeclaration(node)) {
        return
      }

      // skip variable declarations that don't have initializers
      // NOTE: in ES6 spec initializer is required in variable declarations where name is binding pattern
      // so we'll always treat binding elements as initialized
      if (node.kind == SyntaxKind.VariableDeclaration && !node.initializer) {
        return
      }

      val symbol = getSymbolOfNode(node)
      if (symbol.flags & SymbolFlags.FunctionScopedVariable) {
        val localDeclarationSymbol = resolveName(node, (<Identifier>node.name).text, SymbolFlags.Variable, /*nodeNotFoundErrorMessage*/ (), /*nameArg*/ ())
        if (localDeclarationSymbol &&
          localDeclarationSymbol != symbol &&
          localDeclarationSymbol.flags & SymbolFlags.BlockScopedVariable) {
          if (getDeclarationFlagsFromSymbol(localDeclarationSymbol) & NodeFlags.BlockScoped) {
            val varDeclList = getAncestor(localDeclarationSymbol.valueDeclaration, SyntaxKind.VariableDeclarationList)
            val container =
              varDeclList.parent.kind == SyntaxKind.VariableStatement && varDeclList.parent.parent
                ? varDeclList.parent.parent
                : ()

            // names of block-scoped and def scoped variables can collide only
            // if block scoped variable is defined in the def\module\source file scope (because of variable hoisting)
            val namesShareScope =
              container &&
              (container.kind == SyntaxKind.Block && isFunctionLike(container.parent) ||
                container.kind == SyntaxKind.ModuleBlock ||
                container.kind == SyntaxKind.ModuleDeclaration ||
                container.kind == SyntaxKind.SourceFile)

            // here we know that def scoped variable is shadowed by block scoped one
            // if they are defined in the same scope - binder has already reported redeclaration error
            // otherwise if variable has an initializer - show error that initialization will fail
            // since LHS will be block scoped name instead of def scoped
            if (!namesShareScope) {
              val name = symbolToString(localDeclarationSymbol)
              error(node, Diagnostics.Cannot_initialize_outer_scoped_variable_0_in_the_same_scope_as_block_scoped_declaration_1, name, name)
            }
          }
        }
      }
    }

    // Check that a parameter initializer contains no references to parameters declared to the right of itself
    def checkParameterInitializer(node: VariableLikeDeclaration): Unit {
      if (getRootDeclaration(node).kind != SyntaxKind.Parameter) {
        return
      }

      val func = getContainingFunction(node)
      visit(node.initializer)

      def visit(n: Node) {
        if (n.kind == SyntaxKind.Identifier) {
          val referencedSymbol = getNodeLinks(n).resolvedSymbol
          // check FunctionLikeDeclaration.locals (stores parameters\def local variable)
          // if it contains entry with a specified name and if this entry matches the resolved symbol
          if (referencedSymbol && referencedSymbol != unknownSymbol && getSymbol(func.locals, referencedSymbol.name, SymbolFlags.Value) == referencedSymbol) {
            if (referencedSymbol.valueDeclaration.kind == SyntaxKind.Parameter) {
              if (referencedSymbol.valueDeclaration == node) {
                error(n, Diagnostics.Parameter_0_cannot_be_referenced_in_its_initializer, declarationNameToString(node.name))
                return
              }
              if (referencedSymbol.valueDeclaration.pos < node.pos) {
                // legal case - parameter initializer references some parameter strictly on left of current parameter declaration
                return
              }
              // fall through to error reporting
            }
            error(n, Diagnostics.Initializer_of_parameter_0_cannot_reference_identifier_1_declared_after_it, declarationNameToString(node.name), declarationNameToString(<Identifier>n))
          }
        }
        else {
          forEachChild(n, visit)
        }
      }
    }

    // Check variable, parameter, or property declaration
    def checkVariableLikeDeclaration(node: VariableLikeDeclaration) {
      checkDecorators(node)
      checkSourceElement(node.type)
      // For a computed property, just check the initializer and exit
      // Do not use hasDynamicName here, because that returns false for well known symbols.
      // We want to perform checkComputedPropertyName for all computed properties, including
      // well known symbols.
      if (node.name.kind == SyntaxKind.ComputedPropertyName) {
        checkComputedPropertyName(<ComputedPropertyName>node.name)
        if (node.initializer) {
          checkExpressionCached(node.initializer)
        }
      }

      if (node.kind == SyntaxKind.BindingElement) {
        // check computed properties inside property names of binding elements
        if (node.propertyName && node.propertyName.kind == SyntaxKind.ComputedPropertyName) {
          checkComputedPropertyName(<ComputedPropertyName>node.propertyName)
        }
      }

      // For a binding pattern, check contained binding elements
      if (isBindingPattern(node.name)) {
        forEach((<BindingPattern>node.name).elements, checkSourceElement)
      }
      // For a parameter declaration with an initializer, error and exit if the containing def doesn't have a body
      if (node.initializer && getRootDeclaration(node).kind == SyntaxKind.Parameter && nodeIsMissing(getContainingFunction(node).body)) {
        error(node, Diagnostics.A_parameter_initializer_is_only_allowed_in_a_function_or_constructor_implementation)
        return
      }
      // For a binding pattern, validate the initializer and exit
      if (isBindingPattern(node.name)) {
        // Don't validate for-in initializer as it is already an error
        if (node.initializer && node.parent.parent.kind != SyntaxKind.ForInStatement) {
          checkTypeAssignableTo(checkExpressionCached(node.initializer), getWidenedTypeForVariableLikeDeclaration(node), node, /*headMessage*/ ())
          checkParameterInitializer(node)
        }
        return
      }
      val symbol = getSymbolOfNode(node)
      val type = getTypeOfVariableOrParameterOrProperty(symbol)
      if (node == symbol.valueDeclaration) {
        // Node is the primary declaration of the symbol, just validate the initializer
        // Don't validate for-in initializer as it is already an error
        if (node.initializer && node.parent.parent.kind != SyntaxKind.ForInStatement) {
          checkTypeAssignableTo(checkExpressionCached(node.initializer), type, node, /*headMessage*/ ())
          checkParameterInitializer(node)
        }
      }
      else {
        // Node is a secondary declaration, check that type is identical to primary declaration and check that
        // initializer is consistent with type associated with the node
        val declarationType = getWidenedTypeForVariableLikeDeclaration(node)
        if (type != unknownType && declarationType != unknownType && !isTypeIdenticalTo(type, declarationType)) {
          error(node.name, Diagnostics.Subsequent_variable_declarations_must_have_the_same_type_Variable_0_must_be_of_type_1_but_here_has_type_2, declarationNameToString(node.name), typeToString(type), typeToString(declarationType))
        }
        if (node.initializer) {
          checkTypeAssignableTo(checkExpressionCached(node.initializer), declarationType, node, /*headMessage*/ ())
        }
      }
      if (node.kind != SyntaxKind.PropertyDeclaration && node.kind != SyntaxKind.PropertySignature) {
        // We know we don't have a binding pattern or computed name here
        checkExportsOnMergedDeclarations(node)
        if (node.kind == SyntaxKind.VariableDeclaration || node.kind == SyntaxKind.BindingElement) {
          checkVarDeclaredNamesNotShadowed(<VariableDeclaration | BindingElement>node)
        }
        checkCollisionWithCapturedSuperVariable(node, <Identifier>node.name)
        checkCollisionWithCapturedThisVariable(node, <Identifier>node.name)
        checkCollisionWithRequireExportsInGeneratedCode(node, <Identifier>node.name)
        checkCollisionWithGlobalPromiseInGeneratedCode(node, <Identifier>node.name)
      }
    }

    def checkVariableDeclaration(node: VariableDeclaration) {
      checkGrammarVariableDeclaration(node)
      return checkVariableLikeDeclaration(node)
    }

    def checkBindingElement(node: BindingElement) {
      checkGrammarBindingElement(<BindingElement>node)
      return checkVariableLikeDeclaration(node)
    }

    def checkVariableStatement(node: VariableStatement) {
      // Grammar checking
      checkGrammarDecorators(node) || checkGrammarModifiers(node) || checkGrammarVariableDeclarationList(node.declarationList) || checkGrammarForDisallowedLetOrConstStatement(node)

      forEach(node.declarationList.declarations, checkSourceElement)
    }

    def checkGrammarDisallowedModifiersOnObjectLiteralExpressionMethod(node: Node) {
      // We only disallow modifier on a method declaration if it is a property of object-literal-expression
      if (node.modifiers && node.parent.kind == SyntaxKind.ObjectLiteralExpression) {
        if (isAsyncFunctionLike(node)) {
          if (node.modifiers.length > 1) {
            return grammarErrorOnFirstToken(node, Diagnostics.Modifiers_cannot_appear_here)
          }
        }
        else {
          return grammarErrorOnFirstToken(node, Diagnostics.Modifiers_cannot_appear_here)
        }
      }
    }

    def checkExpressionStatement(node: ExpressionStatement) {
      // Grammar checking
      checkGrammarStatementInAmbientContext(node)

      checkExpression(node.expression)
    }

    def checkIfStatement(node: IfStatement) {
      // Grammar checking
      checkGrammarStatementInAmbientContext(node)

      checkExpression(node.expression)
      checkSourceElement(node.thenStatement)

      if (node.thenStatement.kind == SyntaxKind.EmptyStatement) {
        error(node.thenStatement, Diagnostics.The_body_of_an_if_statement_cannot_be_the_empty_statement)
      }

      checkSourceElement(node.elseStatement)
    }

    def checkDoStatement(node: DoStatement) {
      // Grammar checking
      checkGrammarStatementInAmbientContext(node)

      checkSourceElement(node.statement)
      checkExpression(node.expression)
    }

    def checkWhileStatement(node: WhileStatement) {
      // Grammar checking
      checkGrammarStatementInAmbientContext(node)

      checkExpression(node.expression)
      checkSourceElement(node.statement)
    }

    def checkForStatement(node: ForStatement) {
      // Grammar checking
      if (!checkGrammarStatementInAmbientContext(node)) {
        if (node.initializer && node.initializer.kind == SyntaxKind.VariableDeclarationList) {
          checkGrammarVariableDeclarationList(<VariableDeclarationList>node.initializer)
        }
      }

      if (node.initializer) {
        if (node.initializer.kind == SyntaxKind.VariableDeclarationList) {
          forEach((<VariableDeclarationList>node.initializer).declarations, checkVariableDeclaration)
        }
        else {
          checkExpression(<Expression>node.initializer)
        }
      }

      if (node.condition) checkExpression(node.condition)
      if (node.incrementor) checkExpression(node.incrementor)
      checkSourceElement(node.statement)
    }

    def checkForOfStatement(node: ForOfStatement): Unit {
      checkGrammarForInOrForOfStatement(node)

      // Check the LHS and RHS
      // If the LHS is a declaration, just check it as a variable declaration, which will in turn check the RHS
      // via checkRightHandSideOfForOf.
      // If the LHS is an expression, check the LHS, as a destructuring assignment or as a reference.
      // Then check that the RHS is assignable to it.
      if (node.initializer.kind == SyntaxKind.VariableDeclarationList) {
        checkForInOrForOfVariableDeclaration(node)
      }
      else {
        val varExpr = <Expression>node.initializer
        val iteratedType = checkRightHandSideOfForOf(node.expression)

        // There may be a destructuring assignment on the left side
        if (varExpr.kind == SyntaxKind.ArrayLiteralExpression || varExpr.kind == SyntaxKind.ObjectLiteralExpression) {
          // iteratedType may be (). In this case, we still want to check the structure of
          // varExpr, in particular making sure it's a valid LeftHandSideExpression. But we'd like
          // to short circuit the type relation checking as much as possible, so we pass the unknownType.
          checkDestructuringAssignment(varExpr, iteratedType || unknownType)
        }
        else {
          val leftType = checkExpression(varExpr)
          checkReferenceExpression(varExpr, /*invalidReferenceMessage*/ Diagnostics.Invalid_left_hand_side_in_for_of_statement,
            /*constantVariableMessage*/ Diagnostics.The_left_hand_side_of_a_for_of_statement_cannot_be_a_constant_or_a_read_only_property)

          // iteratedType will be () if the rightType was missing properties/signatures
          // required to get its iteratedType (like [Symbol.iterator] or next). This may be
          // because we accessed properties from anyType, or it may have led to an error inside
          // getElementTypeOfIterable.
          if (iteratedType) {
            checkTypeAssignableTo(iteratedType, leftType, varExpr, /*headMessage*/ ())
          }
        }
      }

      checkSourceElement(node.statement)
    }

    def checkForInStatement(node: ForInStatement) {
      // Grammar checking
      checkGrammarForInOrForOfStatement(node)

      // TypeScript 1.0 spec  (April 2014): 5.4
      // In a 'for-in' statement of the form
      // for (var VarDecl in Expr) Statement
      //   VarDecl must be a variable declaration without a type annotation that declares a variable of type Any,
      //   and Expr must be an expression of type Any, an object type, or a type parameter type.
      if (node.initializer.kind == SyntaxKind.VariableDeclarationList) {
        val variable = (<VariableDeclarationList>node.initializer).declarations[0]
        if (variable && isBindingPattern(variable.name)) {
          error(variable.name, Diagnostics.The_left_hand_side_of_a_for_in_statement_cannot_be_a_destructuring_pattern)
        }

        checkForInOrForOfVariableDeclaration(node)
      }
      else {
        // In a 'for-in' statement of the form
        // for (Var in Expr) Statement
        //   Var must be an expression classified as a reference of type Any or the String primitive type,
        //   and Expr must be an expression of type Any, an object type, or a type parameter type.
        val varExpr = <Expression>node.initializer
        val leftType = checkExpression(varExpr)
        if (varExpr.kind == SyntaxKind.ArrayLiteralExpression || varExpr.kind == SyntaxKind.ObjectLiteralExpression) {
          error(varExpr, Diagnostics.The_left_hand_side_of_a_for_in_statement_cannot_be_a_destructuring_pattern)
        }
        else if (!isTypeAnyOrAllConstituentTypesHaveKind(leftType, TypeFlags.StringLike)) {
          error(varExpr, Diagnostics.The_left_hand_side_of_a_for_in_statement_must_be_of_type_string_or_any)
        }
        else {
          // run check only former check succeeded to avoid cascading errors
          checkReferenceExpression(varExpr, Diagnostics.Invalid_left_hand_side_in_for_in_statement,
            Diagnostics.The_left_hand_side_of_a_for_in_statement_cannot_be_a_constant_or_a_read_only_property)
        }
      }

      val rightType = checkExpression(node.expression)
      // unknownType is returned i.e. if node.expression is identifier whose name cannot be resolved
      // in this case error about missing name is already reported - do not report extra one
      if (!isTypeAnyOrAllConstituentTypesHaveKind(rightType, TypeFlags.ObjectType | TypeFlags.TypeParameter)) {
        error(node.expression, Diagnostics.The_right_hand_side_of_a_for_in_statement_must_be_of_type_any_an_object_type_or_a_type_parameter)
      }

      checkSourceElement(node.statement)
    }

    def checkForInOrForOfVariableDeclaration(iterationStatement: ForInStatement | ForOfStatement): Unit {
      val variableDeclarationList = <VariableDeclarationList>iterationStatement.initializer
      // checkGrammarForInOrForOfStatement will check that there is exactly one declaration.
      if (variableDeclarationList.declarations.length >= 1) {
        val decl = variableDeclarationList.declarations[0]
        checkVariableDeclaration(decl)
      }
    }

    def checkRightHandSideOfForOf(rhsExpression: Expression): Type {
      val expressionType = getTypeOfExpression(rhsExpression)
      return checkIteratedTypeOrElementType(expressionType, rhsExpression, /*allowStringInput*/ true)
    }

    def checkIteratedTypeOrElementType(inputType: Type, errorNode: Node, allowStringInput: Boolean): Type {
      if (isTypeAny(inputType)) {
        return inputType
      }

      if (languageVersion >= ScriptTarget.ES6) {
        return checkElementTypeOfIterable(inputType, errorNode)
      }

      if (allowStringInput) {
        return checkElementTypeOfArrayOrString(inputType, errorNode)
      }

      if (isArrayLikeType(inputType)) {
        val indexType = getIndexTypeOfType(inputType, IndexKind.Number)
        if (indexType) {
          return indexType
        }
      }

      error(errorNode, Diagnostics.Type_0_is_not_an_array_type, typeToString(inputType))
      return unknownType
    }

    /**
     * When errorNode is (), it means we should not report any errors.
     */
    def checkElementTypeOfIterable(iterable: Type, errorNode: Node): Type {
      val elementType = getElementTypeOfIterable(iterable, errorNode)
      // Now even though we have extracted the iteratedType, we will have to validate that the type
      // passed in is actually an Iterable.
      if (errorNode && elementType) {
        checkTypeAssignableTo(iterable, createIterableType(elementType), errorNode)
      }

      return elementType || anyType
    }

    /**
     * We want to treat type as an iterable, and get the type it is an iterable of. The iterable
     * must have the following structure (annotated with the names of the variables below):
     *
     * { // iterable
     *   [Symbol.iterator]: { // iteratorFunction
     *     (): Iterator<T>
     *   }
     * }
     *
     * T is the type we are after. At every level that involves analyzing return types
     * of signatures, we union the return types of all the signatures.
     *
     * Another thing to note is that at any step of this process, we could run into a dead end,
     * meaning either the property is missing, or we run into the anyType. If either of these things
     * happens, we return () to signal that we could not find the iterated type. If a property
     * is missing, and the previous step did not result in 'any', then we also give an error if the
     * caller requested it. Then the caller can decide what to do in the case where there is no iterated
     * type. This is different from returning anyType, because that would signify that we have matched the
     * whole pattern and that T (above) is 'any'.
     */
    def getElementTypeOfIterable(type: Type, errorNode: Node): Type {
      if (isTypeAny(type)) {
        return ()
      }

      val typeAsIterable = <IterableOrIteratorType>type
      if (!typeAsIterable.iterableElementType) {
        // As an optimization, if the type is instantiated directly using the globalIterableType (Iterable<Int>),
        // then just grab its type argument.
        if ((type.flags & TypeFlags.Reference) && (<GenericType>type).target == globalIterableType) {
          typeAsIterable.iterableElementType = (<GenericType>type).typeArguments[0]
        }
        else {
          val iteratorFunction = getTypeOfPropertyOfType(type, getPropertyNameForKnownSymbolName("iterator"))
          if (isTypeAny(iteratorFunction)) {
            return ()
          }

          val iteratorFunctionSignatures = iteratorFunction ? getSignaturesOfType(iteratorFunction, SignatureKind.Call) : emptyArray
          if (iteratorFunctionSignatures.length == 0) {
            if (errorNode) {
              error(errorNode, Diagnostics.Type_must_have_a_Symbol_iterator_method_that_returns_an_iterator)
            }
            return ()
          }

          typeAsIterable.iterableElementType = getElementTypeOfIterator(getUnionType(map(iteratorFunctionSignatures, getReturnTypeOfSignature)), errorNode)
        }
      }

      return typeAsIterable.iterableElementType
    }

    /**
     * This def has very similar logic as getElementTypeOfIterable, except that it operates on
     * Iterators instead of Iterables. Here is the structure:
     *
     *  { // iterator
     *    next: { // iteratorNextFunction
     *      (): { // iteratorNextResult
     *        value: T // iteratorNextValue
     *      }
     *    }
     *  }
     *
     */
    def getElementTypeOfIterator(type: Type, errorNode: Node): Type {
      if (isTypeAny(type)) {
        return ()
      }

      val typeAsIterator = <IterableOrIteratorType>type
      if (!typeAsIterator.iteratorElementType) {
        // As an optimization, if the type is instantiated directly using the globalIteratorType (Iterator<Int>),
        // then just grab its type argument.
        if ((type.flags & TypeFlags.Reference) && (<GenericType>type).target == globalIteratorType) {
          typeAsIterator.iteratorElementType = (<GenericType>type).typeArguments[0]
        }
        else {
          val iteratorNextFunction = getTypeOfPropertyOfType(type, "next")
          if (isTypeAny(iteratorNextFunction)) {
            return ()
          }

          val iteratorNextFunctionSignatures = iteratorNextFunction ? getSignaturesOfType(iteratorNextFunction, SignatureKind.Call) : emptyArray
          if (iteratorNextFunctionSignatures.length == 0) {
            if (errorNode) {
              error(errorNode, Diagnostics.An_iterator_must_have_a_next_method)
            }
            return ()
          }

          val iteratorNextResult = getUnionType(map(iteratorNextFunctionSignatures, getReturnTypeOfSignature))
          if (isTypeAny(iteratorNextResult)) {
            return ()
          }

          val iteratorNextValue = getTypeOfPropertyOfType(iteratorNextResult, "value")
          if (!iteratorNextValue) {
            if (errorNode) {
              error(errorNode, Diagnostics.The_type_returned_by_the_next_method_of_an_iterator_must_have_a_value_property)
            }
            return ()
          }

          typeAsIterator.iteratorElementType = iteratorNextValue
        }
      }

      return typeAsIterator.iteratorElementType
    }

    def getElementTypeOfIterableIterator(type: Type): Type {
      if (isTypeAny(type)) {
        return ()
      }

      // As an optimization, if the type is instantiated directly using the globalIterableIteratorType (IterableIterator<Int>),
      // then just grab its type argument.
      if ((type.flags & TypeFlags.Reference) && (<GenericType>type).target == globalIterableIteratorType) {
        return (<GenericType>type).typeArguments[0]
      }

      return getElementTypeOfIterable(type, /*errorNode*/ ()) ||
        getElementTypeOfIterator(type, /*errorNode*/ ())
    }

    /**
     * This def does the following steps:
     *   1. Break up arrayOrStringType (possibly a union) into its String constituents and array constituents.
     *   2. Take the element types of the array constituents.
     *   3. Return the union of the element types, and String if there was a String constituent.
     *
     * For example:
     *   String -> String
     *   Int[] -> Int
     *   String[] | Int[] -> String | Int
     *   String | Int[] -> String | Int
     *   String | String[] | Int[] -> String | Int
     *
     * It also errors if:
     *   1. Some constituent is neither a String nor an array.
     *   2. Some constituent is a String and target is less than ES5 (because in ES3 String is not indexable).
     */
    def checkElementTypeOfArrayOrString(arrayOrStringType: Type, errorNode: Node): Type {
      Debug.assert(languageVersion < ScriptTarget.ES6)

      // After we remove all types that are StringLike, we will know if there was a String constituent
      // based on whether the remaining type is the same as the initial type.
      var arrayType = arrayOrStringType
      if (arrayOrStringType.flags & TypeFlags.Union) {
        arrayType = getUnionType(filter((arrayOrStringType as UnionType).types, t => !(t.flags & TypeFlags.StringLike)))
      }
      else if (arrayOrStringType.flags & TypeFlags.StringLike) {
        arrayType = emptyUnionType
      }
      val hasStringConstituent = arrayOrStringType != arrayType
      var reportedError = false
      if (hasStringConstituent) {
        if (languageVersion < ScriptTarget.ES5) {
          error(errorNode, Diagnostics.Using_a_string_in_a_for_of_statement_is_only_supported_in_ECMAScript_5_and_higher)
          reportedError = true
        }

        // Now that we've removed all the StringLike types, if no constituents remain, then the entire
        // arrayOrStringType was a String.
        if (arrayType == emptyObjectType) {
          return stringType
        }
      }

      if (!isArrayLikeType(arrayType)) {
        if (!reportedError) {
          // Which error we report depends on whether there was a String constituent. For example,
          // if the input type is Int | String, we want to say that Int is not an array type.
          // But if the input was just Int, we want to say that Int is not an array type
          // or a String type.
          val diagnostic = hasStringConstituent
            ? Diagnostics.Type_0_is_not_an_array_type
            : Diagnostics.Type_0_is_not_an_array_type_or_a_string_type
          error(errorNode, diagnostic, typeToString(arrayType))
        }
        return hasStringConstituent ? stringType : unknownType
      }

      val arrayElementType = getIndexTypeOfType(arrayType, IndexKind.Number) || unknownType
      if (hasStringConstituent) {
        // This is just an optimization for the case where arrayOrStringType is String | String[]
        if (arrayElementType.flags & TypeFlags.StringLike) {
          return stringType
        }

        return getUnionType([arrayElementType, stringType])
      }

      return arrayElementType
    }

    def checkBreakOrContinueStatement(node: BreakOrContinueStatement) {
      // Grammar checking
      checkGrammarStatementInAmbientContext(node) || checkGrammarBreakOrContinueStatement(node)

      // TODO: Check that target label is valid
    }

    def isGetAccessorWithAnnotatedSetAccessor(node: FunctionLikeDeclaration) {
      return !!(node.kind == SyntaxKind.GetAccessor && getSetAccessorTypeAnnotationNode(<AccessorDeclaration>getDeclarationOfKind(node.symbol, SyntaxKind.SetAccessor)))
    }

    def checkReturnStatement(node: ReturnStatement) {
      // Grammar checking
      if (!checkGrammarStatementInAmbientContext(node)) {
        val functionBlock = getContainingFunction(node)
        if (!functionBlock) {
          grammarErrorOnFirstToken(node, Diagnostics.A_return_statement_can_only_be_used_within_a_function_body)
        }
      }

      if (node.expression) {
        val func = getContainingFunction(node)
        if (func) {
          val signature = getSignatureFromDeclaration(func)
          val returnType = getReturnTypeOfSignature(signature)
          val exprType = checkExpressionCached(node.expression)

          if (func.asteriskToken) {
            // A generator does not need its return expressions checked against its return type.
            // Instead, the yield expressions are checked against the element type.
            // TODO: Check return expressions of generators when return type tracking is added
            // for generators.
            return
          }

          if (func.kind == SyntaxKind.SetAccessor) {
            error(node.expression, Diagnostics.Setters_cannot_return_a_value)
          }
          else if (func.kind == SyntaxKind.Constructor) {
            if (!checkTypeAssignableTo(exprType, returnType, node.expression)) {
              error(node.expression, Diagnostics.Return_type_of_constructor_signature_must_be_assignable_to_the_instance_type_of_the_class)
            }
          }
          else if (func.type || isGetAccessorWithAnnotatedSetAccessor(func) || returnType.flags & TypeFlags.PredicateType) {
            if (isAsyncFunctionLike(func)) {
              val promisedType = getPromisedType(returnType)
              val awaitedType = checkAwaitedType(exprType, node.expression, Diagnostics.Return_expression_in_async_function_does_not_have_a_valid_callable_then_member)
              if (promisedType) {
                // If the def has a return type, but promisedType is
                // (), an error will be reported in checkAsyncFunctionReturnType
                // so we don't need to report one here.
                checkTypeAssignableTo(awaitedType, promisedType, node.expression)
              }
            }
            else {
              checkTypeAssignableTo(exprType, returnType, node.expression)
            }
          }
        }
      }
    }

    def checkWithStatement(node: WithStatement) {
      // Grammar checking for withStatement
      if (!checkGrammarStatementInAmbientContext(node)) {
        if (node.flags & NodeFlags.AwaitContext) {
          grammarErrorOnFirstToken(node, Diagnostics.with_statements_are_not_allowed_in_an_async_function_block)
        }
      }

      checkExpression(node.expression)
      error(node.expression, Diagnostics.All_symbols_within_a_with_block_will_be_resolved_to_any)
    }

    def checkSwitchStatement(node: SwitchStatement) {
      // Grammar checking
      checkGrammarStatementInAmbientContext(node)

      var firstDefaultClause: CaseOrDefaultClause
      var hasDuplicateDefaultClause = false

      val expressionType = checkExpression(node.expression)
      val expressionTypeIsStringLike = maybeTypeOfKind(expressionType, TypeFlags.StringLike)
      forEach(node.caseBlock.clauses, clause => {
        // Grammar check for duplicate default clauses, skip if we already report duplicate default clause
        if (clause.kind == SyntaxKind.DefaultClause && !hasDuplicateDefaultClause) {
          if (firstDefaultClause == ()) {
            firstDefaultClause = clause
          }
          else {
            val sourceFile = getSourceFileOfNode(node)
            val start = skipTrivia(sourceFile.text, clause.pos)
            val end = clause.statements.length > 0 ? clause.statements[0].pos : clause.end
            grammarErrorAtPos(sourceFile, start, end - start, Diagnostics.A_default_clause_cannot_appear_more_than_once_in_a_switch_statement)
            hasDuplicateDefaultClause = true
          }
        }

        if (produceDiagnostics && clause.kind == SyntaxKind.CaseClause) {
          val caseClause = <CaseClause>clause
          // TypeScript 1.0 spec (April 2014):5.9
          // In a 'switch' statement, each 'case' expression must be of a type that is assignable to or from the type of the 'switch' expression.
          val caseType = checkExpression(caseClause.expression)

          val expressionTypeIsAssignableToCaseType =
            // Permit 'Int[] | "foo"' to be asserted to 'String'.
            (expressionTypeIsStringLike && maybeTypeOfKind(caseType, TypeFlags.StringLike)) ||
            isTypeAssignableTo(expressionType, caseType)

          if (!expressionTypeIsAssignableToCaseType) {
            // 'expressionType is not assignable to caseType', try the reversed check and report errors if it fails
            checkTypeAssignableTo(caseType, expressionType, caseClause.expression, /*headMessage*/ ())
          }
        }
        forEach(clause.statements, checkSourceElement)
      })
    }

    def checkLabeledStatement(node: LabeledStatement) {
      // Grammar checking
      if (!checkGrammarStatementInAmbientContext(node)) {
        var current = node.parent
        while (current) {
          if (isFunctionLike(current)) {
            break
          }
          if (current.kind == SyntaxKind.LabeledStatement && (<LabeledStatement>current).label.text == node.label.text) {
            val sourceFile = getSourceFileOfNode(node)
            grammarErrorOnNode(node.label, Diagnostics.Duplicate_label_0, getTextOfNodeFromSourceText(sourceFile.text, node.label))
            break
          }
          current = current.parent
        }
      }

      // ensure that label is unique
      checkSourceElement(node.statement)
    }

    def checkThrowStatement(node: ThrowStatement) {
      // Grammar checking
      if (!checkGrammarStatementInAmbientContext(node)) {
        if (node.expression == ()) {
          grammarErrorAfterFirstToken(node, Diagnostics.Line_break_not_permitted_here)
        }
      }

      if (node.expression) {
        checkExpression(node.expression)
      }
    }

    def checkTryStatement(node: TryStatement) {
      // Grammar checking
      checkGrammarStatementInAmbientContext(node)

      checkBlock(node.tryBlock)
      val catchClause = node.catchClause
      if (catchClause) {
        // Grammar checking
        if (catchClause.variableDeclaration) {
          if (catchClause.variableDeclaration.name.kind != SyntaxKind.Identifier) {
            grammarErrorOnFirstToken(catchClause.variableDeclaration.name, Diagnostics.Catch_clause_variable_name_must_be_an_identifier)
          }
          else if (catchClause.variableDeclaration.type) {
            grammarErrorOnFirstToken(catchClause.variableDeclaration.type, Diagnostics.Catch_clause_variable_cannot_have_a_type_annotation)
          }
          else if (catchClause.variableDeclaration.initializer) {
            grammarErrorOnFirstToken(catchClause.variableDeclaration.initializer, Diagnostics.Catch_clause_variable_cannot_have_an_initializer)
          }
          else {
            val identifierName = (<Identifier>catchClause.variableDeclaration.name).text
            val locals = catchClause.block.locals
            if (locals && hasProperty(locals, identifierName)) {
              val localSymbol = locals[identifierName]
              if (localSymbol && (localSymbol.flags & SymbolFlags.BlockScopedVariable) != 0) {
                grammarErrorOnNode(localSymbol.valueDeclaration, Diagnostics.Cannot_redeclare_identifier_0_in_catch_clause, identifierName)
              }
            }
          }
        }

        checkBlock(catchClause.block)
      }

      if (node.finallyBlock) {
        checkBlock(node.finallyBlock)
      }
    }

    def checkIndexConstraints(type: Type) {
      val declaredNumberIndexer = getIndexDeclarationOfSymbol(type.symbol, IndexKind.Number)
      val declaredStringIndexer = getIndexDeclarationOfSymbol(type.symbol, IndexKind.String)

      val stringIndexType = getIndexTypeOfType(type, IndexKind.String)
      val numberIndexType = getIndexTypeOfType(type, IndexKind.Number)

      if (stringIndexType || numberIndexType) {
        forEach(getPropertiesOfObjectType(type), prop => {
          val propType = getTypeOfSymbol(prop)
          checkIndexConstraintForProperty(prop, propType, type, declaredStringIndexer, stringIndexType, IndexKind.String)
          checkIndexConstraintForProperty(prop, propType, type, declaredNumberIndexer, numberIndexType, IndexKind.Number)
        })

        if (type.flags & TypeFlags.Class && isClassLike(type.symbol.valueDeclaration)) {
          val classDeclaration = <ClassLikeDeclaration>type.symbol.valueDeclaration
          for (val member of classDeclaration.members) {
            // Only process instance properties with computed names here.
            // Static properties cannot be in conflict with indexers,
            // and properties with literal names were already checked.
            if (!(member.flags & NodeFlags.Static) && hasDynamicName(member)) {
              val propType = getTypeOfSymbol(member.symbol)
              checkIndexConstraintForProperty(member.symbol, propType, type, declaredStringIndexer, stringIndexType, IndexKind.String)
              checkIndexConstraintForProperty(member.symbol, propType, type, declaredNumberIndexer, numberIndexType, IndexKind.Number)
            }
          }
        }
      }

      var errorNode: Node
      if (stringIndexType && numberIndexType) {
        errorNode = declaredNumberIndexer || declaredStringIndexer
        // condition 'errorNode == ()' may appear if types does not declare nor String neither Int indexer
        if (!errorNode && (type.flags & TypeFlags.Interface)) {
          val someBaseTypeHasBothIndexers = forEach(getBaseTypes(<InterfaceType>type), base => getIndexTypeOfType(base, IndexKind.String) && getIndexTypeOfType(base, IndexKind.Number))
          errorNode = someBaseTypeHasBothIndexers ? () : type.symbol.declarations[0]
        }
      }

      if (errorNode && !isTypeAssignableTo(numberIndexType, stringIndexType)) {
        error(errorNode, Diagnostics.Numeric_index_type_0_is_not_assignable_to_string_index_type_1,
          typeToString(numberIndexType), typeToString(stringIndexType))
      }

      def checkIndexConstraintForProperty(
        prop: Symbol,
        propertyType: Type,
        containingType: Type,
        indexDeclaration: Declaration,
        indexType: Type,
        indexKind: IndexKind): Unit {

        if (!indexType) {
          return
        }

        // index is numeric and property name is not valid numeric literal
        if (indexKind == IndexKind.Number && !isNumericName(prop.valueDeclaration.name)) {
          return
        }

        // perform property check if property or indexer is declared in 'type'
        // this allows to rule out cases when both property and indexer are inherited from the base class
        var errorNode: Node
        if (prop.valueDeclaration.name.kind == SyntaxKind.ComputedPropertyName || prop.parent == containingType.symbol) {
          errorNode = prop.valueDeclaration
        }
        else if (indexDeclaration) {
          errorNode = indexDeclaration
        }
        else if (containingType.flags & TypeFlags.Interface) {
          // for interfaces property and indexer might be inherited from different bases
          // check if any base class already has both property and indexer.
          // check should be performed only if 'type' is the first type that brings property\indexer together
          val someBaseClassHasBothPropertyAndIndexer = forEach(getBaseTypes(<InterfaceType>containingType), base => getPropertyOfObjectType(base, prop.name) && getIndexTypeOfType(base, indexKind))
          errorNode = someBaseClassHasBothPropertyAndIndexer ? () : containingType.symbol.declarations[0]
        }

        if (errorNode && !isTypeAssignableTo(propertyType, indexType)) {
          val errorMessage =
            indexKind == IndexKind.String
              ? Diagnostics.Property_0_of_type_1_is_not_assignable_to_string_index_type_2
              : Diagnostics.Property_0_of_type_1_is_not_assignable_to_numeric_index_type_2
          error(errorNode, errorMessage, symbolToString(prop), typeToString(propertyType), typeToString(indexType))
        }
      }
    }

    def checkTypeNameIsReserved(name: DeclarationName, message: DiagnosticMessage): Unit {
      // TS 1.0 spec (April 2014): 3.6.1
      // The predefined type keywords are reserved and cannot be used as names of user defined types.
      switch ((<Identifier>name).text) {
        case "any":
        case "Int":
        case "Boolean":
        case "String":
        case "symbol":
        case "Unit":
          error(name, message, (<Identifier>name).text)
      }
    }

    // Check each type parameter and check that list has no duplicate type parameter declarations
    def checkTypeParameters(typeParameterDeclarations: TypeParameterDeclaration[]) {
      if (typeParameterDeclarations) {
        for (var i = 0, n = typeParameterDeclarations.length; i < n; i++) {
          val node = typeParameterDeclarations[i]
          checkTypeParameter(node)

          if (produceDiagnostics) {
            for (var j = 0; j < i; j++) {
              if (typeParameterDeclarations[j].symbol == node.symbol) {
                error(node.name, Diagnostics.Duplicate_identifier_0, declarationNameToString(node.name))
              }
            }
          }
        }
      }
    }

    def checkClassExpression(node: ClassExpression): Type {
      checkClassLikeDeclaration(node)
      checkNodeDeferred(node)
      return getTypeOfSymbol(getSymbolOfNode(node))
    }

    def checkClassExpressionDeferred(node: ClassExpression) {
      forEach(node.members, checkSourceElement)
    }

    def checkClassDeclaration(node: ClassDeclaration) {
      if (!node.name && !(node.flags & NodeFlags.Default)) {
        grammarErrorOnFirstToken(node, Diagnostics.A_class_declaration_without_the_default_modifier_must_have_a_name)
      }
      checkClassLikeDeclaration(node)
      forEach(node.members, checkSourceElement)
    }

    def checkClassLikeDeclaration(node: ClassLikeDeclaration) {
      checkGrammarClassDeclarationHeritageClauses(node)
      checkDecorators(node)
      if (node.name) {
        checkTypeNameIsReserved(node.name, Diagnostics.Class_name_cannot_be_0)
        checkCollisionWithCapturedThisVariable(node, node.name)
        checkCollisionWithRequireExportsInGeneratedCode(node, node.name)
        checkCollisionWithGlobalPromiseInGeneratedCode(node, node.name)
      }
      checkTypeParameters(node.typeParameters)
      checkExportsOnMergedDeclarations(node)
      val symbol = getSymbolOfNode(node)
      val type = <InterfaceType>getDeclaredTypeOfSymbol(symbol)
      val typeWithThis = getTypeWithThisArgument(type)
      val staticType = <ObjectType>getTypeOfSymbol(symbol)

      val baseTypeNode = getClassExtendsHeritageClauseElement(node)
      if (baseTypeNode) {
        val baseTypes = getBaseTypes(type)
        if (baseTypes.length && produceDiagnostics) {
          val baseType = baseTypes[0]
          val staticBaseType = getBaseConstructorTypeOfClass(type)
          checkSourceElement(baseTypeNode.expression)
          if (baseTypeNode.typeArguments) {
            forEach(baseTypeNode.typeArguments, checkSourceElement)
            for (val constructor of getConstructorsForTypeArguments(staticBaseType, baseTypeNode.typeArguments)) {
              if (!checkTypeArgumentConstraints(constructor.typeParameters, baseTypeNode.typeArguments)) {
                break
              }
            }
          }
          checkTypeAssignableTo(typeWithThis, getTypeWithThisArgument(baseType, type.thisType), node.name || node, Diagnostics.Class_0_incorrectly_extends_base_class_1)
          checkTypeAssignableTo(staticType, getTypeWithoutSignatures(staticBaseType), node.name || node,
            Diagnostics.Class_static_side_0_incorrectly_extends_base_class_static_side_1)

          if (!(staticBaseType.symbol && staticBaseType.symbol.flags & SymbolFlags.Class)) {
            // When the static base type is a "class-like" constructor def (but not actually a class), we verify
            // that all instantiated base constructor signatures return the same type. We can simply compare the type
            // references (as opposed to checking the structure of the types) because elsewhere we have already checked
            // that the base type is a class or trait type (and not, for example, an anonymous object type).
            val constructors = getInstantiatedConstructorsForTypeArguments(staticBaseType, baseTypeNode.typeArguments)
            if (forEach(constructors, sig => getReturnTypeOfSignature(sig) != baseType)) {
              error(baseTypeNode.expression, Diagnostics.Base_constructors_must_all_have_the_same_return_type)
            }
          }
          checkKindsOfPropertyMemberOverrides(type, baseType)
        }
      }

      val implementedTypeNodes = getClassImplementsHeritageClauseElements(node)
      if (implementedTypeNodes) {
        for (val typeRefNode of implementedTypeNodes) {
          if (!isSupportedExpressionWithTypeArguments(typeRefNode)) {
            error(typeRefNode.expression, Diagnostics.A_class_can_only_implement_an_identifier_Slashqualified_name_with_optional_type_arguments)
          }
          checkTypeReferenceNode(typeRefNode)
          if (produceDiagnostics) {
            val t = getTypeFromTypeNode(typeRefNode)
            if (t != unknownType) {
              val declaredType = (t.flags & TypeFlags.Reference) ? (<TypeReference>t).target : t
              if (declaredType.flags & (TypeFlags.Class | TypeFlags.Interface)) {
                checkTypeAssignableTo(typeWithThis, getTypeWithThisArgument(t, type.thisType), node.name || node, Diagnostics.Class_0_incorrectly_implements_interface_1)
              }
              else {
                error(typeRefNode, Diagnostics.A_class_may_only_implement_another_class_or_interface)
              }
            }
          }
        }
      }

      if (produceDiagnostics) {
        checkIndexConstraints(type)
        checkTypeForDuplicateIndexSignatures(node)
      }
    }

    def getTargetSymbol(s: Symbol) {
      // if symbol is instantiated its flags are not copied from the 'target'
      // so we'll need to get back original 'target' symbol to work with correct set of flags
      return s.flags & SymbolFlags.Instantiated ? getSymbolLinks(s).target : s
    }

    def getClassLikeDeclarationOfSymbol(symbol: Symbol): Declaration {
      return forEach(symbol.declarations, d => isClassLike(d) ? d : ())
    }

    def checkKindsOfPropertyMemberOverrides(type: InterfaceType, baseType: ObjectType): Unit {

      // TypeScript 1.0 spec (April 2014): 8.2.3
      // A derived class inherits all members from its base class it doesn't override.
      // Inheritance means that a derived class implicitly contains all non - overridden members of the base class.
      // Both public and private property members are inherited, but only public property members can be overridden.
      // A property member in a derived class is said to override a property member in a base class
      // when the derived class property member has the same name and kind(instance or static)
      // as the base class property member.
      // The type of an overriding property member must be assignable(section 3.8.4)
      // to the type of the overridden property member, or otherwise a compile - time error occurs.
      // Base class instance member functions can be overridden by derived class instance member functions,
      // but not by other kinds of members.
      // Base class instance member variables and accessors can be overridden by
      // derived class instance member variables and accessors, but not by other kinds of members.

      // NOTE: assignability is checked in checkClassDeclaration
      val baseProperties = getPropertiesOfObjectType(baseType)
      for (val baseProperty of baseProperties) {
        val base = getTargetSymbol(baseProperty)

        if (base.flags & SymbolFlags.Prototype) {
          continue
        }

        val derived = getTargetSymbol(getPropertyOfObjectType(type, base.name))
        val baseDeclarationFlags = getDeclarationFlagsFromSymbol(base)

        Debug.assert(!!derived, "derived should point to something, even if it is the base class' declaration.")

        if (derived) {
          // In order to resolve whether the inherited method was overridden in the base class or not,
          // we compare the Symbols obtained. Since getTargetSymbol returns the symbol on the *uninstantiated*
          // type declaration, derived and base resolve to the same symbol even in the case of generic classes.
          if (derived == base) {
            // derived class inherits base without override/redeclaration

            val derivedClassDecl = getClassLikeDeclarationOfSymbol(type.symbol)

            // It is an error to inherit an abstract member without implementing it or being declared abstract.
            // If there is no declaration for the derived class (as in the case of class expressions),
            // then the class cannot be declared abstract.
            if (baseDeclarationFlags & NodeFlags.Abstract && (!derivedClassDecl || !(derivedClassDecl.flags & NodeFlags.Abstract))) {
              if (derivedClassDecl.kind == SyntaxKind.ClassExpression) {
                error(derivedClassDecl, Diagnostics.Non_abstract_class_expression_does_not_implement_inherited_abstract_member_0_from_class_1,
                  symbolToString(baseProperty), typeToString(baseType))
              }
              else {
                error(derivedClassDecl, Diagnostics.Non_abstract_class_0_does_not_implement_inherited_abstract_member_1_from_class_2,
                  typeToString(type), symbolToString(baseProperty), typeToString(baseType))
              }
            }
          }
          else {
            // derived overrides base.
            val derivedDeclarationFlags = getDeclarationFlagsFromSymbol(derived)
            if ((baseDeclarationFlags & NodeFlags.Private) || (derivedDeclarationFlags & NodeFlags.Private)) {
              // either base or derived property is private - not override, skip it
              continue
            }

            if ((baseDeclarationFlags & NodeFlags.Static) != (derivedDeclarationFlags & NodeFlags.Static)) {
              // value of 'static' is not the same for properties - not override, skip it
              continue
            }

            if ((base.flags & derived.flags & SymbolFlags.Method) || ((base.flags & SymbolFlags.PropertyOrAccessor) && (derived.flags & SymbolFlags.PropertyOrAccessor))) {
              // method is overridden with method or property/accessor is overridden with property/accessor - correct case
              continue
            }

            var errorMessage: DiagnosticMessage
            if (base.flags & SymbolFlags.Method) {
              if (derived.flags & SymbolFlags.Accessor) {
                errorMessage = Diagnostics.Class_0_defines_instance_member_function_1_but_extended_class_2_defines_it_as_instance_member_accessor
              }
              else {
                Debug.assert((derived.flags & SymbolFlags.Property) != 0)
                errorMessage = Diagnostics.Class_0_defines_instance_member_function_1_but_extended_class_2_defines_it_as_instance_member_property
              }
            }
            else if (base.flags & SymbolFlags.Property) {
              Debug.assert((derived.flags & SymbolFlags.Method) != 0)
              errorMessage = Diagnostics.Class_0_defines_instance_member_property_1_but_extended_class_2_defines_it_as_instance_member_function
            }
            else {
              Debug.assert((base.flags & SymbolFlags.Accessor) != 0)
              Debug.assert((derived.flags & SymbolFlags.Method) != 0)
              errorMessage = Diagnostics.Class_0_defines_instance_member_accessor_1_but_extended_class_2_defines_it_as_instance_member_function
            }

            error(derived.valueDeclaration.name, errorMessage, typeToString(baseType), symbolToString(base), typeToString(type))
          }
        }
      }
    }

    def isAccessor(kind: SyntaxKind): Boolean {
      return kind == SyntaxKind.GetAccessor || kind == SyntaxKind.SetAccessor
    }

    def areTypeParametersIdentical(list1: TypeParameterDeclaration[], list2: TypeParameterDeclaration[]) {
      if (!list1 && !list2) {
        return true
      }
      if (!list1 || !list2 || list1.length != list2.length) {
        return false
      }
      // TypeScript 1.0 spec (April 2014):
      // When a generic trait has multiple declarations,  all declarations must have identical type parameter
      // lists, i.e. identical type parameter names with identical constraints in identical order.
      for (var i = 0, len = list1.length; i < len; i++) {
        val tp1 = list1[i]
        val tp2 = list2[i]
        if (tp1.name.text != tp2.name.text) {
          return false
        }
        if (!tp1.constraint && !tp2.constraint) {
          continue
        }
        if (!tp1.constraint || !tp2.constraint) {
          return false
        }
        if (!isTypeIdenticalTo(getTypeFromTypeNode(tp1.constraint), getTypeFromTypeNode(tp2.constraint))) {
          return false
        }
      }
      return true
    }

    def checkInheritedPropertiesAreIdentical(type: InterfaceType, typeNode: Node): Boolean {
      val baseTypes = getBaseTypes(type)
      if (baseTypes.length < 2) {
        return true
      }

      val seen: Map<{ prop: Symbol; containingType: Type }> = {}
      forEach(resolveDeclaredMembers(type).declaredProperties, p => { seen[p.name] = { prop: p, containingType: type }; })
      var ok = true

      for (val base of baseTypes) {
        val properties = getPropertiesOfObjectType(getTypeWithThisArgument(base, type.thisType))
        for (val prop of properties) {
          if (!hasProperty(seen, prop.name)) {
            seen[prop.name] = { prop: prop, containingType: base }
          }
          else {
            val existing = seen[prop.name]
            val isInheritedProperty = existing.containingType != type
            if (isInheritedProperty && !isPropertyIdenticalTo(existing.prop, prop)) {
              ok = false

              val typeName1 = typeToString(existing.containingType)
              val typeName2 = typeToString(base)

              var errorInfo = chainDiagnosticMessages((), Diagnostics.Named_property_0_of_types_1_and_2_are_not_identical, symbolToString(prop), typeName1, typeName2)
              errorInfo = chainDiagnosticMessages(errorInfo, Diagnostics.Interface_0_cannot_simultaneously_extend_types_1_and_2, typeToString(type), typeName1, typeName2)
              diagnostics.add(createDiagnosticForNodeFromMessageChain(typeNode, errorInfo))
            }
          }
        }
      }

      return ok
    }

    def checkInterfaceDeclaration(node: InterfaceDeclaration) {
      // Grammar checking
      checkGrammarDecorators(node) || checkGrammarModifiers(node) || checkGrammarInterfaceDeclaration(node)

      checkTypeParameters(node.typeParameters)
      if (produceDiagnostics) {
        checkTypeNameIsReserved(node.name, Diagnostics.Interface_name_cannot_be_0)

        checkExportsOnMergedDeclarations(node)
        val symbol = getSymbolOfNode(node)
        val firstInterfaceDecl = <InterfaceDeclaration>getDeclarationOfKind(symbol, SyntaxKind.InterfaceDeclaration)
        if (symbol.declarations.length > 1) {
          if (node != firstInterfaceDecl && !areTypeParametersIdentical(firstInterfaceDecl.typeParameters, node.typeParameters)) {
            error(node.name, Diagnostics.All_declarations_of_an_interface_must_have_identical_type_parameters)
          }
        }

        // Only check this symbol once
        if (node == firstInterfaceDecl) {
          val type = <InterfaceType>getDeclaredTypeOfSymbol(symbol)
          val typeWithThis = getTypeWithThisArgument(type)
          // run subsequent checks only if first set succeeded
          if (checkInheritedPropertiesAreIdentical(type, node.name)) {
            for (val baseType of getBaseTypes(type)) {
              checkTypeAssignableTo(typeWithThis, getTypeWithThisArgument(baseType, type.thisType), node.name, Diagnostics.Interface_0_incorrectly_extends_interface_1)
            }
            checkIndexConstraints(type)
          }
        }
      }
      forEach(getInterfaceBaseTypeNodes(node), heritageElement => {
        if (!isSupportedExpressionWithTypeArguments(heritageElement)) {
          error(heritageElement.expression, Diagnostics.An_interface_can_only_extend_an_identifier_Slashqualified_name_with_optional_type_arguments)
        }
        checkTypeReferenceNode(heritageElement)
      })

      forEach(node.members, checkSourceElement)

      if (produceDiagnostics) {
        checkTypeForDuplicateIndexSignatures(node)
      }
    }

    def checkTypeAliasDeclaration(node: TypeAliasDeclaration) {
      // Grammar checking
      checkGrammarDecorators(node) || checkGrammarModifiers(node)

      checkTypeNameIsReserved(node.name, Diagnostics.Type_alias_name_cannot_be_0)
      checkSourceElement(node.type)
    }

    def computeEnumMemberValues(node: EnumDeclaration) {
      val nodeLinks = getNodeLinks(node)

      if (!(nodeLinks.flags & NodeCheckFlags.EnumValuesComputed)) {
        val enumSymbol = getSymbolOfNode(node)
        val enumType = getDeclaredTypeOfSymbol(enumSymbol)
        var autoValue = 0; // set to () when enum member is non-constant
        val ambient = isInAmbientContext(node)
        val enumIsConst = isConst(node)

        for (val member of node.members) {
          if (isComputedNonLiteralName(<PropertyName>member.name)) {
            error(member.name, Diagnostics.Computed_property_names_are_not_allowed_in_enums)
          }
          else {
            val text = getTextOfPropertyName(<PropertyName>member.name)
            if (isNumericLiteralName(text)) {
              error(member.name, Diagnostics.An_enum_member_cannot_have_a_numeric_name)
            }
          }

          val previousEnumMemberIsNonConstant = autoValue == ()

          val initializer = member.initializer
          if (initializer) {
            autoValue = computeConstantValueForEnumMemberInitializer(initializer, enumType, enumIsConst, ambient)
          }
          else if (ambient && !enumIsConst) {
            // In ambient enum declarations that specify no val modifier, enum member declarations
            // that omit a value are considered computed members (as opposed to having auto-incremented values assigned).
            autoValue = ()
          }
          else if (previousEnumMemberIsNonConstant) {
            // If the member declaration specifies no value, the member is considered a constant enum member.
            // If the member is the first member in the enum declaration, it is assigned the value zero.
            // Otherwise, it is assigned the value of the immediately preceding member plus one,
            // and an error occurs if the immediately preceding member is not a constant enum member
            error(member.name, Diagnostics.Enum_member_must_have_initializer)
          }

          if (autoValue != ()) {
            getNodeLinks(member).enumMemberValue = autoValue
            autoValue++
          }
        }

        nodeLinks.flags |= NodeCheckFlags.EnumValuesComputed
      }

      def computeConstantValueForEnumMemberInitializer(initializer: Expression, enumType: Type, enumIsConst: Boolean, ambient: Boolean): Int {
        // Controls if error should be reported after evaluation of constant value is completed
        // Can be false if another more precise error was already reported during evaluation.
        var reportError = true
        val value = evalConstant(initializer)

        if (reportError) {
          if (value == ()) {
            if (enumIsConst) {
              error(initializer, Diagnostics.In_const_enum_declarations_member_initializer_must_be_constant_expression)
            }
            else if (ambient) {
              error(initializer, Diagnostics.In_ambient_enum_declarations_member_initializer_must_be_constant_expression)
            }
            else {
              // Only here do we need to check that the initializer is assignable to the enum type.
              checkTypeAssignableTo(checkExpression(initializer), enumType, initializer, /*headMessage*/ ())
            }
          }
          else if (enumIsConst) {
            if (isNaN(value)) {
              error(initializer, Diagnostics.const_enum_member_initializer_was_evaluated_to_disallowed_value_NaN)
            }
            else if (!isFinite(value)) {
              error(initializer, Diagnostics.const_enum_member_initializer_was_evaluated_to_a_non_finite_value)
            }
          }
        }

        return value

        def evalConstant(e: Node): Int {
          switch (e.kind) {
            case SyntaxKind.PrefixUnaryExpression:
              val value = evalConstant((<PrefixUnaryExpression>e).operand)
              if (value == ()) {
                return ()
              }
              switch ((<PrefixUnaryExpression>e).operator) {
                case SyntaxKind.PlusToken: return value
                case SyntaxKind.MinusToken: return -value
                case SyntaxKind.TildeToken: return ~value
              }
              return ()
            case SyntaxKind.BinaryExpression:
              val left = evalConstant((<BinaryExpression>e).left)
              if (left == ()) {
                return ()
              }
              val right = evalConstant((<BinaryExpression>e).right)
              if (right == ()) {
                return ()
              }
              switch ((<BinaryExpression>e).operatorToken.kind) {
                case SyntaxKind.BarToken: return left | right
                case SyntaxKind.AmpersandToken: return left & right
                case SyntaxKind.GreaterThanGreaterThanToken: return left >> right
                case SyntaxKind.GreaterThanGreaterThanGreaterThanToken: return left >>> right
                case SyntaxKind.LessThanLessThanToken: return left << right
                case SyntaxKind.CaretToken: return left ^ right
                case SyntaxKind.AsteriskToken: return left * right
                case SyntaxKind.SlashToken: return left / right
                case SyntaxKind.PlusToken: return left + right
                case SyntaxKind.MinusToken: return left - right
                case SyntaxKind.PercentToken: return left % right
              }
              return ()
            case SyntaxKind.NumericLiteral:
              return +(<LiteralExpression>e).text
            case SyntaxKind.ParenthesizedExpression:
              return evalConstant((<ParenthesizedExpression>e).expression)
            case SyntaxKind.Identifier:
            case SyntaxKind.ElementAccessExpression:
            case SyntaxKind.PropertyAccessExpression:
              val member = initializer.parent
              val currentType = getTypeOfSymbol(getSymbolOfNode(member.parent))
              var enumType: Type
              var propertyName: String

              if (e.kind == SyntaxKind.Identifier) {
                // unqualified names can refer to member that reside in different declaration of the enum so just doing name resolution won't work.
                // instead pick current enum type and later try to fetch member from the type
                enumType = currentType
                propertyName = (<Identifier>e).text
              }
              else {
                var expression: Expression
                if (e.kind == SyntaxKind.ElementAccessExpression) {
                  if ((<ElementAccessExpression>e).argumentExpression == () ||
                    (<ElementAccessExpression>e).argumentExpression.kind != SyntaxKind.StringLiteral) {
                    return ()
                  }
                  expression = (<ElementAccessExpression>e).expression
                  propertyName = (<LiteralExpression>(<ElementAccessExpression>e).argumentExpression).text
                }
                else {
                  expression = (<PropertyAccessExpression>e).expression
                  propertyName = (<PropertyAccessExpression>e).name.text
                }

                // expression part in ElementAccess\PropertyAccess should be either identifier or dottedName
                var current = expression
                while (current) {
                  if (current.kind == SyntaxKind.Identifier) {
                    break
                  }
                  else if (current.kind == SyntaxKind.PropertyAccessExpression) {
                    current = (<ElementAccessExpression>current).expression
                  }
                  else {
                    return ()
                  }
                }

                enumType = checkExpression(expression)
                // allow references to constant members of other enums
                if (!(enumType.symbol && (enumType.symbol.flags & SymbolFlags.Enum))) {
                  return ()
                }
              }

              if (propertyName == ()) {
                return ()
              }

              val property = getPropertyOfObjectType(enumType, propertyName)
              if (!property || !(property.flags & SymbolFlags.EnumMember)) {
                return ()
              }

              val propertyDecl = property.valueDeclaration
              // self references are illegal
              if (member == propertyDecl) {
                return ()
              }

              // illegal case: forward reference
              if (!isBlockScopedNameDeclaredBeforeUse(propertyDecl, member)) {
                reportError = false
                error(e, Diagnostics.A_member_initializer_in_a_enum_declaration_cannot_reference_members_declared_after_it_including_members_defined_in_other_enums)
                return ()
              }

              return <Int>getNodeLinks(propertyDecl).enumMemberValue
          }
        }
      }
    }

    def checkEnumDeclaration(node: EnumDeclaration) {
      if (!produceDiagnostics) {
        return
      }

      // Grammar checking
      checkGrammarDecorators(node) || checkGrammarModifiers(node)

      checkTypeNameIsReserved(node.name, Diagnostics.Enum_name_cannot_be_0)
      checkCollisionWithCapturedThisVariable(node, node.name)
      checkCollisionWithRequireExportsInGeneratedCode(node, node.name)
      checkCollisionWithGlobalPromiseInGeneratedCode(node, node.name)
      checkExportsOnMergedDeclarations(node)

      computeEnumMemberValues(node)

      val enumIsConst = isConst(node)
      if (compilerOptions.isolatedModules && enumIsConst && isInAmbientContext(node)) {
        error(node.name, Diagnostics.Ambient_const_enums_are_not_allowed_when_the_isolatedModules_flag_is_provided)
      }

      // Spec 2014 - Section 9.3:
      // It isn't possible for one enum declaration to continue the automatic numbering sequence of another,
      // and when an enum type has multiple declarations, only one declaration is permitted to omit a value
      // for the first member.
      //
      // Only perform this check once per symbol
      val enumSymbol = getSymbolOfNode(node)
      val firstDeclaration = getDeclarationOfKind(enumSymbol, node.kind)
      if (node == firstDeclaration) {
        if (enumSymbol.declarations.length > 1) {
          // check that val is placed\omitted on all enum declarations
          forEach(enumSymbol.declarations, decl => {
            if (isConstEnumDeclaration(decl) != enumIsConst) {
              error(decl.name, Diagnostics.Enum_declarations_must_all_be_const_or_non_const)
            }
          })
        }

        var seenEnumMissingInitialInitializer = false
        forEach(enumSymbol.declarations, declaration => {
          // return true if we hit a violation of the rule, false otherwise
          if (declaration.kind != SyntaxKind.EnumDeclaration) {
            return false
          }

          val enumDeclaration = <EnumDeclaration>declaration
          if (!enumDeclaration.members.length) {
            return false
          }

          val firstEnumMember = enumDeclaration.members[0]
          if (!firstEnumMember.initializer) {
            if (seenEnumMissingInitialInitializer) {
              error(firstEnumMember.name, Diagnostics.In_an_enum_with_multiple_declarations_only_one_declaration_can_omit_an_initializer_for_its_first_enum_element)
            }
            else {
              seenEnumMissingInitialInitializer = true
            }
          }
        })
      }
    }

    def getFirstNonAmbientClassOrFunctionDeclaration(symbol: Symbol): Declaration {
      val declarations = symbol.declarations
      for (val declaration of declarations) {
        if ((declaration.kind == SyntaxKind.ClassDeclaration ||
          (declaration.kind == SyntaxKind.FunctionDeclaration && nodeIsPresent((<FunctionLikeDeclaration>declaration).body))) &&
          !isInAmbientContext(declaration)) {
          return declaration
        }
      }
      return ()
    }

    def inSameLexicalScope(node1: Node, node2: Node) {
      val container1 = getEnclosingBlockScopeContainer(node1)
      val container2 = getEnclosingBlockScopeContainer(node2)
      if (isGlobalSourceFile(container1)) {
        return isGlobalSourceFile(container2)
      }
      else if (isGlobalSourceFile(container2)) {
        return false
      }
      else {
        return container1 == container2
      }
    }

    def checkModuleDeclaration(node: ModuleDeclaration) {
      if (produceDiagnostics) {
        // Grammar checking
        val isGlobalAugmentation = isGlobalScopeAugmentation(node)
        val inAmbientContext = isInAmbientContext(node)
        if (isGlobalAugmentation && !inAmbientContext) {
          error(node.name, Diagnostics.Augmentations_for_the_global_scope_should_have_declare_modifier_unless_they_appear_in_already_ambient_context)
        }

        val isAmbientExternalModule = isAmbientModule(node)
        val contextErrorMessage = isAmbientExternalModule
          ? Diagnostics.An_ambient_module_declaration_is_only_allowed_at_the_top_level_in_a_file
          : Diagnostics.A_namespace_declaration_is_only_allowed_in_a_namespace_or_module
        if (checkGrammarModuleElementContext(node, contextErrorMessage)) {
          // If we hit a module declaration in an illegal context, just bail out to avoid cascading errors.
          return
        }

        if (!checkGrammarDecorators(node) && !checkGrammarModifiers(node)) {
          if (!inAmbientContext && node.name.kind == SyntaxKind.StringLiteral) {
            grammarErrorOnNode(node.name, Diagnostics.Only_ambient_modules_can_use_quoted_names)
          }
        }

        checkCollisionWithCapturedThisVariable(node, node.name)
        checkCollisionWithRequireExportsInGeneratedCode(node, node.name)
        checkCollisionWithGlobalPromiseInGeneratedCode(node, node.name)
        checkExportsOnMergedDeclarations(node)
        val symbol = getSymbolOfNode(node)

        // The following checks only apply on a non-ambient instantiated module declaration.
        if (symbol.flags & SymbolFlags.ValueModule
          && symbol.declarations.length > 1
          && !inAmbientContext
          && isInstantiatedModule(node, compilerOptions.preserveConstEnums || compilerOptions.isolatedModules)) {
          val firstNonAmbientClassOrFunc = getFirstNonAmbientClassOrFunctionDeclaration(symbol)
          if (firstNonAmbientClassOrFunc) {
            if (getSourceFileOfNode(node) != getSourceFileOfNode(firstNonAmbientClassOrFunc)) {
              error(node.name, Diagnostics.A_namespace_declaration_cannot_be_in_a_different_file_from_a_class_or_function_with_which_it_is_merged)
            }
            else if (node.pos < firstNonAmbientClassOrFunc.pos) {
              error(node.name, Diagnostics.A_namespace_declaration_cannot_be_located_prior_to_a_class_or_function_with_which_it_is_merged)
            }
          }

          // if the module merges with a class declaration in the same lexical scope,
          // we need to track this to ensure the correct emit.
          val mergedClass = getDeclarationOfKind(symbol, SyntaxKind.ClassDeclaration)
          if (mergedClass &&
            inSameLexicalScope(node, mergedClass)) {
            getNodeLinks(node).flags |= NodeCheckFlags.LexicalModuleMergesWithClass
          }
        }

        if (isAmbientExternalModule) {
          if (isExternalModuleAugmentation(node)) {
            // body of the augmentation should be checked for consistency only if augmentation was applied to its target (either global scope or module)
            // otherwise we'll be swamped in cascading errors.
            // We can detect if augmentation was applied using following rules:
            // - augmentation for a global scope is always applied
            // - augmentation for some external module is applied if symbol for augmentation is merged (it was combined with target module).
            val checkBody = isGlobalAugmentation || (getSymbolOfNode(node).flags & SymbolFlags.Merged)
            if (checkBody) {
              // body of ambient external module is always a module block
              for (val statement of (<ModuleBlock>node.body).statements) {
                checkModuleAugmentationElement(statement, isGlobalAugmentation)
              }
            }
          }
          else if (isGlobalSourceFile(node.parent)) {
            if (isGlobalAugmentation) {
              error(node.name, Diagnostics.Augmentations_for_the_global_scope_can_only_be_directly_nested_in_external_modules_or_ambient_module_declarations)
            }
            else if (isExternalModuleNameRelative(node.name.text)) {
              error(node.name, Diagnostics.Ambient_module_declaration_cannot_specify_relative_module_name)
            }
          }
          else {
            if (isGlobalAugmentation) {
              error(node.name, Diagnostics.Augmentations_for_the_global_scope_can_only_be_directly_nested_in_external_modules_or_ambient_module_declarations)
            }
            else {
              // Node is not an augmentation and is not located on the script level.
              // This means that this is declaration of ambient module that is located in other module or package which is prohibited.
              error(node.name, Diagnostics.Ambient_modules_cannot_be_nested_in_other_modules_or_namespaces)
            }
          }
        }
      }
      checkSourceElement(node.body)
    }

    def checkModuleAugmentationElement(node: Node, isGlobalAugmentation: Boolean): Unit {
      switch (node.kind) {
        case SyntaxKind.VariableStatement:
          // error each individual name in variable statement instead of marking the entire variable statement
          for (val decl of (<VariableStatement>node).declarationList.declarations) {
            checkModuleAugmentationElement(decl, isGlobalAugmentation)
          }
          break
        case SyntaxKind.ExportAssignment:
        case SyntaxKind.ExportDeclaration:
          grammarErrorOnFirstToken(node, Diagnostics.Exports_and_export_assignments_are_not_permitted_in_module_augmentations)
          break
        case SyntaxKind.ImportEqualsDeclaration:
          if ((<ImportEqualsDeclaration>node).moduleReference.kind != SyntaxKind.StringLiteral) {
            error((<ImportEqualsDeclaration>node).name, Diagnostics.Module_augmentation_cannot_introduce_new_names_in_the_top_level_scope)
            break
          }
          // fallthrough
        case SyntaxKind.ImportDeclaration:
          grammarErrorOnFirstToken(node, Diagnostics.Imports_are_not_permitted_in_module_augmentations_Consider_moving_them_to_the_enclosing_external_module)
          break
        case SyntaxKind.BindingElement:
        case SyntaxKind.VariableDeclaration:
          val name = (<VariableDeclaration | BindingElement>node).name
          if (isBindingPattern(name)) {
            for (val el of name.elements) {
              // mark individual names in binding pattern
              checkModuleAugmentationElement(el, isGlobalAugmentation)
            }
            break
          }
          // fallthrough
        case SyntaxKind.ClassDeclaration:
        case SyntaxKind.EnumDeclaration:
        case SyntaxKind.FunctionDeclaration:
        case SyntaxKind.InterfaceDeclaration:
        case SyntaxKind.ModuleDeclaration:
        case SyntaxKind.TypeAliasDeclaration:
          val symbol = getSymbolOfNode(node)
          if (symbol) {
            // module augmentations cannot introduce new names on the top level scope of the module
            // this is done it two steps
            // 1. quick check - if symbol for node is not merged - this is local symbol to this augmentation - report error
            // 2. main check - report error if value declaration of the parent symbol is module augmentation)
            var reportError = !(symbol.flags & SymbolFlags.Merged)
            if (!reportError) {
              if (isGlobalAugmentation) {
                // global symbol should not have parent since it is not explicitly exported
                reportError = symbol.parent != ()
              }
              else {
                // symbol should not originate in augmentation
                reportError = isExternalModuleAugmentation(symbol.parent.valueDeclaration)
              }
            }
            if (reportError) {
              error(node, Diagnostics.Module_augmentation_cannot_introduce_new_names_in_the_top_level_scope)
            }
          }
          break
      }
    }

    def getFirstIdentifier(node: EntityName | Expression): Identifier {
      while (true) {
        if (node.kind == SyntaxKind.QualifiedName) {
          node = (<QualifiedName>node).left
        }
        else if (node.kind == SyntaxKind.PropertyAccessExpression) {
          node = (<PropertyAccessExpression>node).expression
        }
        else {
          break
        }
      }
      Debug.assert(node.kind == SyntaxKind.Identifier)
      return <Identifier>node
    }

    def checkExternalImportOrExportDeclaration(node: ImportDeclaration | ImportEqualsDeclaration | ExportDeclaration): Boolean {
      val moduleName = getExternalModuleName(node)
      if (!nodeIsMissing(moduleName) && moduleName.kind != SyntaxKind.StringLiteral) {
        error(moduleName, Diagnostics.String_literal_expected)
        return false
      }
      val inAmbientExternalModule = node.parent.kind == SyntaxKind.ModuleBlock && isAmbientModule(<ModuleDeclaration>node.parent.parent)
      if (node.parent.kind != SyntaxKind.SourceFile && !inAmbientExternalModule) {
        error(moduleName, node.kind == SyntaxKind.ExportDeclaration ?
          Diagnostics.Export_declarations_are_not_permitted_in_a_namespace :
          Diagnostics.Import_declarations_in_a_namespace_cannot_reference_a_module)
        return false
      }
      if (inAmbientExternalModule && isExternalModuleNameRelative((<LiteralExpression>moduleName).text)) {
        // we have already reported errors on top level imports\exports in external module augmentations in checkModuleDeclaration
        // no need to do this again.
        if (!isTopLevelInExternalModuleAugmentation(node)) {
          // TypeScript 1.0 spec (April 2013): 12.1.6
          // An ExternalImportDeclaration in an AmbientExternalModuleDeclaration may reference
          // other external modules only through top - level external module names.
          // Relative external module names are not permitted.
          error(node, Diagnostics.Import_or_export_declaration_in_an_ambient_module_declaration_cannot_reference_module_through_relative_module_name)
          return false
        }
      }
      return true
    }

    def checkAliasSymbol(node: ImportEqualsDeclaration | ImportClause | NamespaceImport | ImportSpecifier | ExportSpecifier) {
      val symbol = getSymbolOfNode(node)
      val target = resolveAlias(symbol)
      if (target != unknownSymbol) {
        val excludedMeanings =
          (symbol.flags & SymbolFlags.Value ? SymbolFlags.Value : 0) |
          (symbol.flags & SymbolFlags.Type ? SymbolFlags.Type : 0) |
          (symbol.flags & SymbolFlags.Namespace ? SymbolFlags.Namespace : 0)
        if (target.flags & excludedMeanings) {
          val message = node.kind == SyntaxKind.ExportSpecifier ?
            Diagnostics.Export_declaration_conflicts_with_exported_declaration_of_0 :
            Diagnostics.Import_declaration_conflicts_with_local_declaration_of_0
          error(node, message, symbolToString(symbol))
        }
      }
    }

    def checkImportBinding(node: ImportEqualsDeclaration | ImportClause | NamespaceImport | ImportSpecifier) {
      checkCollisionWithCapturedThisVariable(node, node.name)
      checkCollisionWithRequireExportsInGeneratedCode(node, node.name)
      checkCollisionWithGlobalPromiseInGeneratedCode(node, node.name)
      checkAliasSymbol(node)
    }

    def checkImportDeclaration(node: ImportDeclaration) {
      if (checkGrammarModuleElementContext(node, Diagnostics.An_import_declaration_can_only_be_used_in_a_namespace_or_module)) {
        // If we hit an import declaration in an illegal context, just bail out to avoid cascading errors.
        return
      }
      if (!checkGrammarDecorators(node) && !checkGrammarModifiers(node) && (node.flags & NodeFlags.Modifier)) {
        grammarErrorOnFirstToken(node, Diagnostics.An_import_declaration_cannot_have_modifiers)
      }
      if (checkExternalImportOrExportDeclaration(node)) {
        val importClause = node.importClause
        if (importClause) {
          if (importClause.name) {
            checkImportBinding(importClause)
          }
          if (importClause.namedBindings) {
            if (importClause.namedBindings.kind == SyntaxKind.NamespaceImport) {
              checkImportBinding(<NamespaceImport>importClause.namedBindings)
            }
            else {
              forEach((<NamedImports>importClause.namedBindings).elements, checkImportBinding)
            }
          }
        }
      }
    }

    def checkImportEqualsDeclaration(node: ImportEqualsDeclaration) {
      if (checkGrammarModuleElementContext(node, Diagnostics.An_import_declaration_can_only_be_used_in_a_namespace_or_module)) {
        // If we hit an import declaration in an illegal context, just bail out to avoid cascading errors.
        return
      }

      checkGrammarDecorators(node) || checkGrammarModifiers(node)
      if (isInternalModuleImportEqualsDeclaration(node) || checkExternalImportOrExportDeclaration(node)) {
        checkImportBinding(node)
        if (node.flags & NodeFlags.Export) {
          markExportAsReferenced(node)
        }
        if (isInternalModuleImportEqualsDeclaration(node)) {
          val target = resolveAlias(getSymbolOfNode(node))
          if (target != unknownSymbol) {
            if (target.flags & SymbolFlags.Value) {
              // Target is a value symbol, check that it is not hidden by a local declaration with the same name
              val moduleName = getFirstIdentifier(<EntityName>node.moduleReference)
              if (!(resolveEntityName(moduleName, SymbolFlags.Value | SymbolFlags.Namespace).flags & SymbolFlags.Namespace)) {
                error(moduleName, Diagnostics.Module_0_is_hidden_by_a_local_declaration_with_the_same_name, declarationNameToString(moduleName))
              }
            }
            if (target.flags & SymbolFlags.Type) {
              checkTypeNameIsReserved(node.name, Diagnostics.Import_name_cannot_be_0)
            }
          }
        }
        else {
          if (modulekind == ModuleKind.ES6 && !isInAmbientContext(node)) {
            // Import equals declaration is deprecated in es6 or above
            grammarErrorOnNode(node, Diagnostics.Import_assignment_cannot_be_used_when_targeting_ECMAScript_6_modules_Consider_using_import_Asterisk_as_ns_from_mod_import_a_from_mod_import_d_from_mod_or_another_module_format_instead)
          }
        }
      }
    }

    def checkExportDeclaration(node: ExportDeclaration) {
      if (checkGrammarModuleElementContext(node, Diagnostics.An_export_declaration_can_only_be_used_in_a_module)) {
        // If we hit an in an illegal context, just bail out to avoid cascading errors.
        return
      }

      if (!checkGrammarDecorators(node) && !checkGrammarModifiers(node) && (node.flags & NodeFlags.Modifier)) {
        grammarErrorOnFirstToken(node, Diagnostics.An_export_declaration_cannot_have_modifiers)
      }

      if (!node.moduleSpecifier || checkExternalImportOrExportDeclaration(node)) {
        if (node.exportClause) {
          // { x, y }
          // { x, y } from "foo"
          forEach(node.exportClause.elements, checkExportSpecifier)

          val inAmbientExternalModule = node.parent.kind == SyntaxKind.ModuleBlock && isAmbientModule(node.parent.parent)
          if (node.parent.kind != SyntaxKind.SourceFile && !inAmbientExternalModule) {
            error(node, Diagnostics.Export_declarations_are_not_permitted_in_a_namespace)
          }
        }
        else {
          // * from "foo"
          val moduleSymbol = resolveExternalModuleName(node, node.moduleSpecifier)
          if (moduleSymbol && hasExportAssignmentSymbol(moduleSymbol)) {
            error(node.moduleSpecifier, Diagnostics.Module_0_uses_export_and_cannot_be_used_with_export_Asterisk, symbolToString(moduleSymbol))
          }
        }
      }
    }

    def checkGrammarModuleElementContext(node: Statement, errorMessage: DiagnosticMessage): Boolean {
      if (node.parent.kind != SyntaxKind.SourceFile && node.parent.kind != SyntaxKind.ModuleBlock && node.parent.kind != SyntaxKind.ModuleDeclaration) {
        return grammarErrorOnFirstToken(node, errorMessage)
      }
    }

    def checkExportSpecifier(node: ExportSpecifier) {
      checkAliasSymbol(node)
      if (!(<ExportDeclaration>node.parent.parent).moduleSpecifier) {
        val exportedName = node.propertyName || node.name
        // find immediate value referenced by exported name (SymbolFlags.Alias is set so we don't chase down aliases)
        val symbol = resolveName(exportedName, exportedName.text, SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace | SymbolFlags.Alias,
          /*nameNotFoundMessage*/ (), /*nameArg*/ ())
        if (symbol && (symbol == undefinedSymbol || isGlobalSourceFile(getDeclarationContainer(symbol.declarations[0])))) {
          error(exportedName, Diagnostics.Cannot_re_export_name_that_is_not_defined_in_the_module)
        }
        else {
          markExportAsReferenced(node)
        }
      }
    }

    def checkExportAssignment(node: ExportAssignment) {
      if (checkGrammarModuleElementContext(node, Diagnostics.An_export_assignment_can_only_be_used_in_a_module)) {
        // If we hit an assignment in an illegal context, just bail out to avoid cascading errors.
        return
      }

      val container = node.parent.kind == SyntaxKind.SourceFile ? <SourceFile>node.parent : <ModuleDeclaration>node.parent.parent
      if (container.kind == SyntaxKind.ModuleDeclaration && !isAmbientModule(container)) {
        error(node, Diagnostics.An_export_assignment_cannot_be_used_in_a_namespace)
        return
      }
      // Grammar checking
      if (!checkGrammarDecorators(node) && !checkGrammarModifiers(node) && (node.flags & NodeFlags.Modifier)) {
        grammarErrorOnFirstToken(node, Diagnostics.An_export_assignment_cannot_have_modifiers)
      }
      if (node.expression.kind == SyntaxKind.Identifier) {
        markExportAsReferenced(node)
      }
      else {
        checkExpressionCached(node.expression)
      }

      checkExternalModuleExports(<SourceFile | ModuleDeclaration>container)

      if (node.isExportEquals && !isInAmbientContext(node)) {
        if (modulekind == ModuleKind.ES6) {
          // assignment is not supported in es6 modules
          grammarErrorOnNode(node, Diagnostics.Export_assignment_cannot_be_used_when_targeting_ECMAScript_6_modules_Consider_using_export_default_or_another_module_format_instead)
        }
        else if (modulekind == ModuleKind.System) {
          // system modules does not support assignment
          grammarErrorOnNode(node, Diagnostics.Export_assignment_is_not_supported_when_module_flag_is_system)
        }
      }
    }

    def hasExportedMembers(moduleSymbol: Symbol) {
      for (val id in moduleSymbol.exports) {
        if (id != "export=") {
          return true
        }
      }
      return false
    }

    def checkExternalModuleExports(node: SourceFile | ModuleDeclaration) {
      val moduleSymbol = getSymbolOfNode(node)
      val links = getSymbolLinks(moduleSymbol)
      if (!links.exportsChecked) {
        val exportEqualsSymbol = moduleSymbol.exports["export="]
        if (exportEqualsSymbol && hasExportedMembers(moduleSymbol)) {
          val declaration = getDeclarationOfAliasSymbol(exportEqualsSymbol) || exportEqualsSymbol.valueDeclaration
          if (!isTopLevelInExternalModuleAugmentation(declaration)) {
            error(declaration, Diagnostics.An_export_assignment_cannot_be_used_in_a_module_with_other_exported_elements)
          }
        }
        // Checks for * conflicts
        val exports = getExportsOfModule(moduleSymbol)
        for (val id in exports) {
          if (id == "__export") {
            continue
          }
          val { declarations, flags } = exports[id]
          // ECMA262: 15.2.1.1 It is a Syntax Error if the ExportedNames of ModuleItemList contains any duplicate entries. (TS Exceptions: namespaces, def overloads, enums, and interfaces)
          if (!(flags & (SymbolFlags.Namespace | SymbolFlags.Interface | SymbolFlags.Enum)) && (flags & SymbolFlags.TypeAlias ? declarations.length - 1 : declarations.length) > 1) {
            val exportedDeclarations: Declaration[] = filter(declarations, isNotOverload)
            if (exportedDeclarations.length > 1) {
              for (val declaration of exportedDeclarations) {
                diagnostics.add(createDiagnosticForNode(declaration, Diagnostics.Cannot_redeclare_exported_variable_0, id))
              }
            }
          }
        }
        links.exportsChecked = true
      }

      def isNotOverload(declaration: Declaration): Boolean {
        return declaration.kind != SyntaxKind.FunctionDeclaration || !!(declaration as FunctionDeclaration).body
      }
    }


    def checkSourceElement(node: Node): Unit {
      if (!node) {
        return
      }

      val kind = node.kind
      if (cancellationToken) {
        // Only bother checking on a few construct kinds.  We don't want to be excessively
        // hitting the cancellation token on every node we check.
        switch (kind) {
          case SyntaxKind.ModuleDeclaration:
          case SyntaxKind.ClassDeclaration:
          case SyntaxKind.InterfaceDeclaration:
          case SyntaxKind.FunctionDeclaration:
            cancellationToken.throwIfCancellationRequested()
        }
      }

      switch (kind) {
        case SyntaxKind.TypeParameter:
          return checkTypeParameter(<TypeParameterDeclaration>node)
        case SyntaxKind.Parameter:
          return checkParameter(<ParameterDeclaration>node)
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
          return checkPropertyDeclaration(<PropertyDeclaration>node)
        case SyntaxKind.FunctionType:
        case SyntaxKind.ConstructorType:
        case SyntaxKind.CallSignature:
        case SyntaxKind.ConstructSignature:
          return checkSignatureDeclaration(<SignatureDeclaration>node)
        case SyntaxKind.IndexSignature:
          return checkSignatureDeclaration(<SignatureDeclaration>node)
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.MethodSignature:
          return checkMethodDeclaration(<MethodDeclaration>node)
        case SyntaxKind.Constructor:
          return checkConstructorDeclaration(<ConstructorDeclaration>node)
        case SyntaxKind.GetAccessor:
        case SyntaxKind.SetAccessor:
          return checkAccessorDeclaration(<AccessorDeclaration>node)
        case SyntaxKind.TypeReference:
          return checkTypeReferenceNode(<TypeReferenceNode>node)
        case SyntaxKind.TypePredicate:
          return checkTypePredicate(<TypePredicateNode>node)
        case SyntaxKind.TypeQuery:
          return checkTypeQuery(<TypeQueryNode>node)
        case SyntaxKind.TypeLiteral:
          return checkTypeLiteral(<TypeLiteralNode>node)
        case SyntaxKind.ArrayType:
          return checkArrayType(<ArrayTypeNode>node)
        case SyntaxKind.TupleType:
          return checkTupleType(<TupleTypeNode>node)
        case SyntaxKind.UnionType:
        case SyntaxKind.IntersectionType:
          return checkUnionOrIntersectionType(<UnionOrIntersectionTypeNode>node)
        case SyntaxKind.ParenthesizedType:
          return checkSourceElement((<ParenthesizedTypeNode>node).type)
        case SyntaxKind.FunctionDeclaration:
          return checkFunctionDeclaration(<FunctionDeclaration>node)
        case SyntaxKind.Block:
        case SyntaxKind.ModuleBlock:
          return checkBlock(<Block>node)
        case SyntaxKind.VariableStatement:
          return checkVariableStatement(<VariableStatement>node)
        case SyntaxKind.ExpressionStatement:
          return checkExpressionStatement(<ExpressionStatement>node)
        case SyntaxKind.IfStatement:
          return checkIfStatement(<IfStatement>node)
        case SyntaxKind.DoStatement:
          return checkDoStatement(<DoStatement>node)
        case SyntaxKind.WhileStatement:
          return checkWhileStatement(<WhileStatement>node)
        case SyntaxKind.ForStatement:
          return checkForStatement(<ForStatement>node)
        case SyntaxKind.ForInStatement:
          return checkForInStatement(<ForInStatement>node)
        case SyntaxKind.ForOfStatement:
          return checkForOfStatement(<ForOfStatement>node)
        case SyntaxKind.ContinueStatement:
        case SyntaxKind.BreakStatement:
          return checkBreakOrContinueStatement(<BreakOrContinueStatement>node)
        case SyntaxKind.ReturnStatement:
          return checkReturnStatement(<ReturnStatement>node)
        case SyntaxKind.WithStatement:
          return checkWithStatement(<WithStatement>node)
        case SyntaxKind.SwitchStatement:
          return checkSwitchStatement(<SwitchStatement>node)
        case SyntaxKind.LabeledStatement:
          return checkLabeledStatement(<LabeledStatement>node)
        case SyntaxKind.ThrowStatement:
          return checkThrowStatement(<ThrowStatement>node)
        case SyntaxKind.TryStatement:
          return checkTryStatement(<TryStatement>node)
        case SyntaxKind.VariableDeclaration:
          return checkVariableDeclaration(<VariableDeclaration>node)
        case SyntaxKind.BindingElement:
          return checkBindingElement(<BindingElement>node)
        case SyntaxKind.ClassDeclaration:
          return checkClassDeclaration(<ClassDeclaration>node)
        case SyntaxKind.InterfaceDeclaration:
          return checkInterfaceDeclaration(<InterfaceDeclaration>node)
        case SyntaxKind.TypeAliasDeclaration:
          return checkTypeAliasDeclaration(<TypeAliasDeclaration>node)
        case SyntaxKind.EnumDeclaration:
          return checkEnumDeclaration(<EnumDeclaration>node)
        case SyntaxKind.ModuleDeclaration:
          return checkModuleDeclaration(<ModuleDeclaration>node)
        case SyntaxKind.ImportDeclaration:
          return checkImportDeclaration(<ImportDeclaration>node)
        case SyntaxKind.ImportEqualsDeclaration:
          return checkImportEqualsDeclaration(<ImportEqualsDeclaration>node)
        case SyntaxKind.ExportDeclaration:
          return checkExportDeclaration(<ExportDeclaration>node)
        case SyntaxKind.ExportAssignment:
          return checkExportAssignment(<ExportAssignment>node)
        case SyntaxKind.EmptyStatement:
          checkGrammarStatementInAmbientContext(node)
          return
        case SyntaxKind.DebuggerStatement:
          checkGrammarStatementInAmbientContext(node)
          return
        case SyntaxKind.MissingDeclaration:
          return checkMissingDeclaration(node)
      }
    }

    // Function and class expression bodies are checked after all statements in the enclosing body. This is
    // to ensure constructs like the following are permitted:
    //   val foo = def () {
    //    val s = foo()
    //    return "hello"
    //   }
    // Here, performing a full type check of the body of the def expression whilst in the process of
    // determining the type of foo would cause foo to be given type any because of the recursive reference.
    // Delaying the type check of the body ensures foo has been assigned a type.
    def checkNodeDeferred(node: Node) {
      if (deferredNodes) {
        deferredNodes.push(node)
      }
    }

    def checkDeferredNodes() {
      for (val node of deferredNodes) {
        switch (node.kind) {
          case SyntaxKind.FunctionExpression:
          case SyntaxKind.ArrowFunction:
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.MethodSignature:
            checkFunctionExpressionOrObjectLiteralMethodDeferred(<FunctionExpression>node)
            break
          case SyntaxKind.GetAccessor:
          case SyntaxKind.SetAccessor:
            checkAccessorDeferred(<AccessorDeclaration>node)
            break
          case SyntaxKind.ClassExpression:
            checkClassExpressionDeferred(<ClassExpression>node)
            break
        }
      }
    }

    def checkSourceFile(node: SourceFile) {
      val start = new Date().getTime()

      checkSourceFileWorker(node)

      checkTime += new Date().getTime() - start
    }

    // Fully type check a source file and collect the relevant diagnostics.
    def checkSourceFileWorker(node: SourceFile) {
      val links = getNodeLinks(node)
      if (!(links.flags & NodeCheckFlags.TypeChecked)) {
        // Check whether the file has declared it is the default lib,
        // and whether the user has specifically chosen to avoid checking it.
        if (compilerOptions.skipDefaultLibCheck) {
          // If the user specified '--noLib' and a file has a '/// <reference no-default-lib="true"/>',
          // then we should treat that file as a default lib.
          if (node.hasNoDefaultLib) {
            return
          }
        }

        // Grammar checking
        checkGrammarSourceFile(node)

        potentialThisCollisions.length = 0

        deferredNodes = []
        forEach(node.statements, checkSourceElement)
        checkDeferredNodes()
        deferredNodes = ()

        if (isExternalOrCommonJsModule(node)) {
          checkExternalModuleExports(node)
        }

        if (potentialThisCollisions.length) {
          forEach(potentialThisCollisions, checkIfThisIsCapturedInEnclosingScope)
          potentialThisCollisions.length = 0
        }

        links.flags |= NodeCheckFlags.TypeChecked
      }
    }

    def getDiagnostics(sourceFile: SourceFile, ct: CancellationToken): Diagnostic[] {
      try {
        // Record the cancellation token so it can be checked later on during checkSourceElement.
        // Do this in a finally block so we can ensure that it gets reset back to nothing after
        // this call is done.
        cancellationToken = ct
        return getDiagnosticsWorker(sourceFile)
      }
      finally {
        cancellationToken = ()
      }
    }

    def getDiagnosticsWorker(sourceFile: SourceFile): Diagnostic[] {
      throwIfNonDiagnosticsProducing()
      if (sourceFile) {
        checkSourceFile(sourceFile)
        return diagnostics.getDiagnostics(sourceFile.fileName)
      }
      forEach(host.getSourceFiles(), checkSourceFile)
      return diagnostics.getDiagnostics()
    }

    def getGlobalDiagnostics(): Diagnostic[] {
      throwIfNonDiagnosticsProducing()
      return diagnostics.getGlobalDiagnostics()
    }

    def throwIfNonDiagnosticsProducing() {
      if (!produceDiagnostics) {
        throw new Error("Trying to get diagnostics from a type checker that does not produce them.")
      }
    }

    // Language service support

    def isInsideWithStatementBody(node: Node): Boolean {
      if (node) {
        while (node.parent) {
          if (node.parent.kind == SyntaxKind.WithStatement && (<WithStatement>node.parent).statement == node) {
            return true
          }
          node = node.parent
        }
      }

      return false
    }

    def getSymbolsInScope(location: Node, meaning: SymbolFlags): Symbol[] {
      val symbols: SymbolTable = {}
      var memberFlags: NodeFlags = 0

      if (isInsideWithStatementBody(location)) {
        // We cannot answer semantic questions within a with block, do not proceed any further
        return []
      }

      populateSymbols()

      return symbolsToArray(symbols)

      def populateSymbols() {
        while (location) {
          if (location.locals && !isGlobalSourceFile(location)) {
            copySymbols(location.locals, meaning)
          }

          switch (location.kind) {
            case SyntaxKind.SourceFile:
              if (!isExternalOrCommonJsModule(<SourceFile>location)) {
                break
              }
            case SyntaxKind.ModuleDeclaration:
              copySymbols(getSymbolOfNode(location).exports, meaning & SymbolFlags.ModuleMember)
              break
            case SyntaxKind.EnumDeclaration:
              copySymbols(getSymbolOfNode(location).exports, meaning & SymbolFlags.EnumMember)
              break
            case SyntaxKind.ClassExpression:
              val className = (<ClassExpression>location).name
              if (className) {
                copySymbol(location.symbol, meaning)
              }
            // fall through; this fall-through is necessary because we would like to handle
            // type parameter inside class expression similar to how we handle it in classDeclaration and trait Declaration
            case SyntaxKind.ClassDeclaration:
            case SyntaxKind.InterfaceDeclaration:
              // If we didn't come from static member of class or trait,
              // add the type parameters into the symbol table
              // (type parameters of classDeclaration/classExpression and trait are in member property of the symbol.
              // Note: that the memberFlags come from previous iteration.
              if (!(memberFlags & NodeFlags.Static)) {
                copySymbols(getSymbolOfNode(location).members, meaning & SymbolFlags.Type)
              }
              break
            case SyntaxKind.FunctionExpression:
              val funcName = (<FunctionExpression>location).name
              if (funcName) {
                copySymbol(location.symbol, meaning)
              }
              break
          }

          if (introducesArgumentsExoticObject(location)) {
            copySymbol(argumentsSymbol, meaning)
          }

          memberFlags = location.flags
          location = location.parent
        }

        copySymbols(globals, meaning)
      }

      /**
       * Copy the given symbol into symbol tables if the symbol has the given meaning
       * and it doesn't already existed in the symbol table
       * @param key a key for storing in symbol table; if (), use symbol.name
       * @param symbol the symbol to be added into symbol table
       * @param meaning meaning of symbol to filter by before adding to symbol table
       */
      def copySymbol(symbol: Symbol, meaning: SymbolFlags): Unit {
        if (symbol.flags & meaning) {
          val id = symbol.name
          // We will copy all symbol regardless of its reserved name because
          // symbolsToArray will check whether the key is a reserved name and
          // it will not copy symbol with reserved name to the array
          if (!hasProperty(symbols, id)) {
            symbols[id] = symbol
          }
        }
      }

      def copySymbols(source: SymbolTable, meaning: SymbolFlags): Unit {
        if (meaning) {
          for (val id in source) {
            val symbol = source[id]
            copySymbol(symbol, meaning)
          }
        }
      }
    }

    def isTypeDeclarationName(name: Node): Boolean {
      return name.kind == SyntaxKind.Identifier &&
        isTypeDeclaration(name.parent) &&
        (<Declaration>name.parent).name == name
    }

    def isTypeDeclaration(node: Node): Boolean {
      switch (node.kind) {
        case SyntaxKind.TypeParameter:
        case SyntaxKind.ClassDeclaration:
        case SyntaxKind.InterfaceDeclaration:
        case SyntaxKind.TypeAliasDeclaration:
        case SyntaxKind.EnumDeclaration:
          return true
      }
    }

    // True if the given identifier is part of a type reference
    def isTypeReferenceIdentifier(entityName: EntityName): Boolean {
      var node: Node = entityName
      while (node.parent && node.parent.kind == SyntaxKind.QualifiedName) {
        node = node.parent
      }

      return node.parent && node.parent.kind == SyntaxKind.TypeReference
    }

    def isHeritageClauseElementIdentifier(entityName: Node): Boolean {
      var node = entityName
      while (node.parent && node.parent.kind == SyntaxKind.PropertyAccessExpression) {
        node = node.parent
      }

      return node.parent && node.parent.kind == SyntaxKind.ExpressionWithTypeArguments
    }

    def getLeftSideOfImportEqualsOrExportAssignment(nodeOnRightSide: EntityName): ImportEqualsDeclaration | ExportAssignment {
      while (nodeOnRightSide.parent.kind == SyntaxKind.QualifiedName) {
        nodeOnRightSide = <QualifiedName>nodeOnRightSide.parent
      }

      if (nodeOnRightSide.parent.kind == SyntaxKind.ImportEqualsDeclaration) {
        return (<ImportEqualsDeclaration>nodeOnRightSide.parent).moduleReference == nodeOnRightSide && <ImportEqualsDeclaration>nodeOnRightSide.parent
      }

      if (nodeOnRightSide.parent.kind == SyntaxKind.ExportAssignment) {
        return (<ExportAssignment>nodeOnRightSide.parent).expression == <Node>nodeOnRightSide && <ExportAssignment>nodeOnRightSide.parent
      }

      return ()
    }

    def isInRightSideOfImportOrExportAssignment(node: EntityName) {
      return getLeftSideOfImportEqualsOrExportAssignment(node) != ()
    }

    def getSymbolOfEntityNameOrPropertyAccessExpression(entityName: EntityName | PropertyAccessExpression): Symbol {
      if (isDeclarationName(entityName)) {
        return getSymbolOfNode(entityName.parent)
      }

      if (isInJavaScriptFile(entityName) && entityName.parent.kind == SyntaxKind.PropertyAccessExpression) {
        val specialPropertyAssignmentKind = getSpecialPropertyAssignmentKind(entityName.parent.parent)
        switch (specialPropertyAssignmentKind) {
          case SpecialPropertyAssignmentKind.ExportsProperty:
          case SpecialPropertyAssignmentKind.PrototypeProperty:
            return getSymbolOfNode(entityName.parent)
          case SpecialPropertyAssignmentKind.ThisProperty:
          case SpecialPropertyAssignmentKind.ModuleExports:
            return getSymbolOfNode(entityName.parent.parent)
          default:
            // Fall through if it is not a special property assignment
        }
      }

      if (entityName.parent.kind == SyntaxKind.ExportAssignment) {
        return resolveEntityName(<Identifier>entityName,
          /*all meanings*/ SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace | SymbolFlags.Alias)
      }

      if (entityName.kind != SyntaxKind.PropertyAccessExpression) {
        if (isInRightSideOfImportOrExportAssignment(<EntityName>entityName)) {
          // Since we already checked for ExportAssignment, this really could only be an Import
          return getSymbolOfPartOfRightHandSideOfImportEquals(<EntityName>entityName)
        }
      }

      if (isRightSideOfQualifiedNameOrPropertyAccess(entityName)) {
        entityName = <QualifiedName | PropertyAccessExpression>entityName.parent
      }

      if (isHeritageClauseElementIdentifier(<EntityName>entityName)) {
        var meaning = SymbolFlags.None

        // In an trait or class, we're definitely interested in a type.
        if (entityName.parent.kind == SyntaxKind.ExpressionWithTypeArguments) {
          meaning = SymbolFlags.Type

          // In a class 'extends' clause we are also looking for a value.
          if (isExpressionWithTypeArgumentsInClassExtendsClause(entityName.parent)) {
            meaning |= SymbolFlags.Value
          }
        }
        else {
          meaning = SymbolFlags.Namespace
        }

        meaning |= SymbolFlags.Alias
        return resolveEntityName(<EntityName>entityName, meaning)
      }
      else if ((entityName.parent.kind == SyntaxKind.JsxOpeningElement) ||
        (entityName.parent.kind == SyntaxKind.JsxSelfClosingElement) ||
        (entityName.parent.kind == SyntaxKind.JsxClosingElement)) {

        return getJsxTagSymbol(<JsxOpeningLikeElement>entityName.parent)
      }
      else if (isExpression(entityName)) {
        if (nodeIsMissing(entityName)) {
          // Missing entity name.
          return ()
        }

        if (entityName.kind == SyntaxKind.Identifier) {
          // Include aliases in the meaning, this ensures that we do not follow aliases to where they point and instead
          // return the alias symbol.
          val meaning: SymbolFlags = SymbolFlags.Value | SymbolFlags.Alias
          return resolveEntityName(<Identifier>entityName, meaning)
        }
        else if (entityName.kind == SyntaxKind.PropertyAccessExpression) {
          val symbol = getNodeLinks(entityName).resolvedSymbol
          if (!symbol) {
            checkPropertyAccessExpression(<PropertyAccessExpression>entityName)
          }
          return getNodeLinks(entityName).resolvedSymbol
        }
        else if (entityName.kind == SyntaxKind.QualifiedName) {
          val symbol = getNodeLinks(entityName).resolvedSymbol
          if (!symbol) {
            checkQualifiedName(<QualifiedName>entityName)
          }
          return getNodeLinks(entityName).resolvedSymbol
        }
      }
      else if (isTypeReferenceIdentifier(<EntityName>entityName)) {
        var meaning = entityName.parent.kind == SyntaxKind.TypeReference ? SymbolFlags.Type : SymbolFlags.Namespace
        // Include aliases in the meaning, this ensures that we do not follow aliases to where they point and instead
        // return the alias symbol.
        meaning |= SymbolFlags.Alias
        return resolveEntityName(<EntityName>entityName, meaning)
      }
      else if (entityName.parent.kind == SyntaxKind.JsxAttribute) {
        return getJsxAttributePropertySymbol(<JsxAttribute>entityName.parent)
      }

      if (entityName.parent.kind == SyntaxKind.TypePredicate) {
        return resolveEntityName(<Identifier>entityName, /*meaning*/ SymbolFlags.FunctionScopedVariable)
      }

      // Do we want to return () here?
      return ()
    }

    def getSymbolAtLocation(node: Node) {
      if (isInsideWithStatementBody(node)) {
        // We cannot answer semantic questions within a with block, do not proceed any further
        return ()
      }

      if (isDeclarationName(node)) {
        // This is a declaration, call getSymbolOfNode
        return getSymbolOfNode(node.parent)
      }

      if (node.kind == SyntaxKind.Identifier) {
        if (isInRightSideOfImportOrExportAssignment(<Identifier>node)) {
          return node.parent.kind == SyntaxKind.ExportAssignment
            ? getSymbolOfEntityNameOrPropertyAccessExpression(<Identifier>node)
            : getSymbolOfPartOfRightHandSideOfImportEquals(<Identifier>node)
        }
        else if (node.parent.kind == SyntaxKind.BindingElement &&
          node.parent.parent.kind == SyntaxKind.ObjectBindingPattern &&
          node == (<BindingElement>node.parent).propertyName) {
          val typeOfPattern = getTypeOfNode(node.parent.parent)
          val propertyDeclaration = typeOfPattern && getPropertyOfType(typeOfPattern, (<Identifier>node).text)

          if (propertyDeclaration) {
            return propertyDeclaration
          }
        }
      }

      switch (node.kind) {
        case SyntaxKind.Identifier:
        case SyntaxKind.PropertyAccessExpression:
        case SyntaxKind.QualifiedName:
          return getSymbolOfEntityNameOrPropertyAccessExpression(<EntityName | PropertyAccessExpression>node)

        case SyntaxKind.ThisKeyword:
        case SyntaxKind.SuperKeyword:
          val type = isExpression(node) ? checkExpression(<Expression>node) : getTypeFromTypeNode(<TypeNode>node)
          return type.symbol

        case SyntaxKind.ThisType:
          return getTypeFromTypeNode(<TypeNode>node).symbol

        case SyntaxKind.ConstructorKeyword:
          // constructor keyword for an overload, should take us to the definition if it exist
          val constructorDeclaration = node.parent
          if (constructorDeclaration && constructorDeclaration.kind == SyntaxKind.Constructor) {
            return (<ClassDeclaration>constructorDeclaration.parent).symbol
          }
          return ()

        case SyntaxKind.StringLiteral:
          // External module name in an import declaration
          if ((isExternalModuleImportEqualsDeclaration(node.parent.parent) &&
            getExternalModuleImportEqualsDeclarationExpression(node.parent.parent) == node) ||
            ((node.parent.kind == SyntaxKind.ImportDeclaration || node.parent.kind == SyntaxKind.ExportDeclaration) &&
              (<ImportDeclaration>node.parent).moduleSpecifier == node)) {
            return resolveExternalModuleName(node, <LiteralExpression>node)
          }
        // Fall through

        case SyntaxKind.NumericLiteral:
          // index access
          if (node.parent.kind == SyntaxKind.ElementAccessExpression && (<ElementAccessExpression>node.parent).argumentExpression == node) {
            val objectType = checkExpression((<ElementAccessExpression>node.parent).expression)
            if (objectType == unknownType) return ()
            val apparentType = getApparentType(objectType)
            if (apparentType == unknownType) return ()
            return getPropertyOfType(apparentType, (<LiteralExpression>node).text)
          }
          break
      }
      return ()
    }

    def getShorthandAssignmentValueSymbol(location: Node): Symbol {
      // The def returns a value symbol of an identifier in the short-hand property assignment.
      // This is necessary as an identifier in short-hand property assignment can contains two meaning:
      // property name and property value.
      if (location && location.kind == SyntaxKind.ShorthandPropertyAssignment) {
        return resolveEntityName((<ShorthandPropertyAssignment>location).name, SymbolFlags.Value | SymbolFlags.Alias)
      }
      return ()
    }

    /** Returns the target of an specifier without following aliases */
    def getExportSpecifierLocalTargetSymbol(node: ExportSpecifier): Symbol {
      return (<ExportDeclaration>node.parent.parent).moduleSpecifier ?
        getExternalModuleMember(<ExportDeclaration>node.parent.parent, node) :
        resolveEntityName(node.propertyName || node.name, SymbolFlags.Value | SymbolFlags.Type | SymbolFlags.Namespace | SymbolFlags.Alias)
    }

    def getTypeOfNode(node: Node): Type {
      if (isInsideWithStatementBody(node)) {
        // We cannot answer semantic questions within a with block, do not proceed any further
        return unknownType
      }

      if (isTypeNode(node)) {
        return getTypeFromTypeNode(<TypeNode>node)
      }

      if (isExpression(node)) {
        return getTypeOfExpression(<Expression>node)
      }

      if (isExpressionWithTypeArgumentsInClassExtendsClause(node)) {
        // A SyntaxKind.ExpressionWithTypeArguments is considered a type node, except when it occurs in the
        // extends clause of a class. We handle that case here.
        return getBaseTypes(<InterfaceType>getDeclaredTypeOfSymbol(getSymbolOfNode(node.parent.parent)))[0]
      }

      if (isTypeDeclaration(node)) {
        // In this case, we call getSymbolOfNode instead of getSymbolAtLocation because it is a declaration
        val symbol = getSymbolOfNode(node)
        return getDeclaredTypeOfSymbol(symbol)
      }

      if (isTypeDeclarationName(node)) {
        val symbol = getSymbolAtLocation(node)
        return symbol && getDeclaredTypeOfSymbol(symbol)
      }

      if (isDeclaration(node)) {
        // In this case, we call getSymbolOfNode instead of getSymbolAtLocation because it is a declaration
        val symbol = getSymbolOfNode(node)
        return getTypeOfSymbol(symbol)
      }

      if (isDeclarationName(node)) {
        val symbol = getSymbolAtLocation(node)
        return symbol && getTypeOfSymbol(symbol)
      }

      if (isBindingPattern(node)) {
        return getTypeForVariableLikeDeclaration(<VariableLikeDeclaration>node.parent)
      }

      if (isInRightSideOfImportOrExportAssignment(<Identifier>node)) {
        val symbol = getSymbolAtLocation(node)
        val declaredType = symbol && getDeclaredTypeOfSymbol(symbol)
        return declaredType != unknownType ? declaredType : getTypeOfSymbol(symbol)
      }

      return unknownType
    }


    def getTypeOfExpression(expr: Expression): Type {
      if (isRightSideOfQualifiedNameOrPropertyAccess(expr)) {
        expr = <Expression>expr.parent
      }
      return checkExpression(expr)
    }

    /**
      * Gets either the static or instance type of a class element, based on
      * whether the element is declared as "static".
      */
    def getParentTypeOfClassElement(node: ClassElement) {
      val classSymbol = getSymbolOfNode(node.parent)
      return node.flags & NodeFlags.Static
        ? getTypeOfSymbol(classSymbol)
        : getDeclaredTypeOfSymbol(classSymbol)
    }

    // Return the list of properties of the given type, augmented with properties from Function
    // if the type has call or construct signatures
    def getAugmentedPropertiesOfType(type: Type): Symbol[] {
      type = getApparentType(type)
      val propsByName = createSymbolTable(getPropertiesOfType(type))
      if (getSignaturesOfType(type, SignatureKind.Call).length || getSignaturesOfType(type, SignatureKind.Construct).length) {
        forEach(getPropertiesOfType(globalFunctionType), p => {
          if (!hasProperty(propsByName, p.name)) {
            propsByName[p.name] = p
          }
        })
      }
      return getNamedMembers(propsByName)
    }

    def getRootSymbols(symbol: Symbol): Symbol[] {
      if (symbol.flags & SymbolFlags.SyntheticProperty) {
        val symbols: Symbol[] = []
        val name = symbol.name
        forEach(getSymbolLinks(symbol).containingType.types, t => {
          val symbol = getPropertyOfType(t, name)
          if (symbol) {
            symbols.push(symbol)
          }
        })
        return symbols
      }
      else if (symbol.flags & SymbolFlags.Transient) {
        val target = getSymbolLinks(symbol).target
        if (target) {
          return [target]
        }
      }
      return [symbol]
    }

    // Emitter support

    def isArgumentsLocalBinding(node: Identifier): Boolean {
      return getReferencedValueSymbol(node) == argumentsSymbol
    }

    def moduleExportsSomeValue(moduleReferenceExpression: Expression): Boolean {
      var moduleSymbol = resolveExternalModuleName(moduleReferenceExpression.parent, moduleReferenceExpression)
      if (!moduleSymbol) {
        // module not found - be conservative
        return true
      }

      val hasExportAssignment = hasExportAssignmentSymbol(moduleSymbol)
      // if module has assignment then 'resolveExternalModuleSymbol' will return resolved symbol for assignment
      // otherwise it will return moduleSymbol itself
      moduleSymbol = resolveExternalModuleSymbol(moduleSymbol)

      val symbolLinks = getSymbolLinks(moduleSymbol)
      if (symbolLinks.exportsSomeValue == ()) {
        // for assignments - check if resolved symbol for RHS is itself a value
        // otherwise - check if at least one is value
        symbolLinks.exportsSomeValue = hasExportAssignment
          ? !!(moduleSymbol.flags & SymbolFlags.Value)
          : forEachValue(getExportsOfModule(moduleSymbol), isValue)
      }

      return symbolLinks.exportsSomeValue

      def isValue(s: Symbol): Boolean {
        s = resolveSymbol(s)
        return s && !!(s.flags & SymbolFlags.Value)
      }
    }

    // When resolved as an expression identifier, if the given node references an exported entity, return the declaration
    // node of the exported entity's container. Otherwise, return ().
    def getReferencedExportContainer(node: Identifier): SourceFile | ModuleDeclaration | EnumDeclaration {
      var symbol = getReferencedValueSymbol(node)
      if (symbol) {
        if (symbol.flags & SymbolFlags.ExportValue) {
          // If we reference an exported entity within the same module declaration, then whether
          // we prefix depends on the kind of entity. SymbolFlags.ExportHasLocal encompasses all the
          // kinds that we do NOT prefix.
          val exportSymbol = getMergedSymbol(symbol.exportSymbol)
          if (exportSymbol.flags & SymbolFlags.ExportHasLocal) {
            return ()
          }
          symbol = exportSymbol
        }
        val parentSymbol = getParentOfSymbol(symbol)
        if (parentSymbol) {
          if (parentSymbol.flags & SymbolFlags.ValueModule && parentSymbol.valueDeclaration.kind == SyntaxKind.SourceFile) {
            return <SourceFile>parentSymbol.valueDeclaration
          }
          for (var n = node.parent; n; n = n.parent) {
            if ((n.kind == SyntaxKind.ModuleDeclaration || n.kind == SyntaxKind.EnumDeclaration) && getSymbolOfNode(n) == parentSymbol) {
              return <ModuleDeclaration | EnumDeclaration>n
            }
          }
        }
      }
    }

    // When resolved as an expression identifier, if the given node references an import, return the declaration of
    // that import. Otherwise, return ().
    def getReferencedImportDeclaration(node: Identifier): Declaration {
      val symbol = getReferencedValueSymbol(node)
      return symbol && symbol.flags & SymbolFlags.Alias ? getDeclarationOfAliasSymbol(symbol) : ()
    }

    def isSymbolOfDeclarationWithCollidingName(symbol: Symbol): Boolean {
      if (symbol.flags & SymbolFlags.BlockScoped) {
        val links = getSymbolLinks(symbol)
        if (links.isDeclarationWithCollidingName == ()) {
          val container = getEnclosingBlockScopeContainer(symbol.valueDeclaration)
          if (isStatementWithLocals(container)) {
            val nodeLinks = getNodeLinks(symbol.valueDeclaration)
            if (!!resolveName(container.parent, symbol.name, SymbolFlags.Value, /*nameNotFoundMessage*/ (), /*nameArg*/ ())) {
              // redeclaration - always should be renamed
              links.isDeclarationWithCollidingName = true
            }
            else if (nodeLinks.flags & NodeCheckFlags.CapturedBlockScopedBinding) {
              // binding is captured in the def
              // should be renamed if:
              // - binding is not top level - top level bindings never collide with anything
              // AND
              //   - binding is not declared in loop, should be renamed to avoid name reuse across siblings
              //   var a, b
              //   { var x = 1; a = () => x;  }
              //   { var x = 100; b = () => x; }
              //   console.log(a()); // should print '1'
              //   console.log(b()); // should print '100'
              //   OR
              //   - binding is declared inside loop but not in inside initializer of iteration statement or directly inside loop body
              //   * variables from initializer are passed to rewritten loop body as parameters so they are not captured directly
              //   * variables that are declared immediately in loop body will become top level variable after loop is rewritten and thus
              //     they will not collide with anything
              val isDeclaredInLoop = nodeLinks.flags & NodeCheckFlags.BlockScopedBindingInLoop
              val inLoopInitializer = isIterationStatement(container, /*lookInLabeledStatements*/ false)
              val inLoopBodyBlock = container.kind == SyntaxKind.Block && isIterationStatement(container.parent, /*lookInLabeledStatements*/ false)

              links.isDeclarationWithCollidingName = !isBlockScopedContainerTopLevel(container) && (!isDeclaredInLoop || (!inLoopInitializer && !inLoopBodyBlock))
            }
            else {
              links.isDeclarationWithCollidingName = false
            }
          }
        }
        return links.isDeclarationWithCollidingName
      }
      return false
    }

    // When resolved as an expression identifier, if the given node references a nested block scoped entity with
    // a name that either hides an existing name or might hide it when compiled downlevel,
    // return the declaration of that entity. Otherwise, return ().
    def getReferencedDeclarationWithCollidingName(node: Identifier): Declaration {
      val symbol = getReferencedValueSymbol(node)
      return symbol && isSymbolOfDeclarationWithCollidingName(symbol) ? symbol.valueDeclaration : ()
    }

    // Return true if the given node is a declaration of a nested block scoped entity with a name that either hides an
    // existing name or might hide a name when compiled downlevel
    def isDeclarationWithCollidingName(node: Declaration): Boolean {
      return isSymbolOfDeclarationWithCollidingName(getSymbolOfNode(node))
    }

    def isValueAliasDeclaration(node: Node): Boolean {
      switch (node.kind) {
        case SyntaxKind.ImportEqualsDeclaration:
        case SyntaxKind.ImportClause:
        case SyntaxKind.NamespaceImport:
        case SyntaxKind.ImportSpecifier:
        case SyntaxKind.ExportSpecifier:
          return isAliasResolvedToValue(getSymbolOfNode(node))
        case SyntaxKind.ExportDeclaration:
          val exportClause = (<ExportDeclaration>node).exportClause
          return exportClause && forEach(exportClause.elements, isValueAliasDeclaration)
        case SyntaxKind.ExportAssignment:
          return (<ExportAssignment>node).expression && (<ExportAssignment>node).expression.kind == SyntaxKind.Identifier ? isAliasResolvedToValue(getSymbolOfNode(node)) : true
      }
      return false
    }

    def isTopLevelValueImportEqualsWithEntityName(node: ImportEqualsDeclaration): Boolean {
      if (node.parent.kind != SyntaxKind.SourceFile || !isInternalModuleImportEqualsDeclaration(node)) {
        // parent is not source file or it is not reference to internal module
        return false
      }

      val isValue = isAliasResolvedToValue(getSymbolOfNode(node))
      return isValue && node.moduleReference && !nodeIsMissing(node.moduleReference)
    }

    def isAliasResolvedToValue(symbol: Symbol): Boolean {
      val target = resolveAlias(symbol)
      if (target == unknownSymbol && compilerOptions.isolatedModules) {
        return true
      }
      // val enums and modules that contain only val enums are not considered values from the emit perspective
      // unless 'preserveConstEnums' option is set to true
      return target != unknownSymbol &&
        target &&
        target.flags & SymbolFlags.Value &&
        (compilerOptions.preserveConstEnums || !isConstEnumOrConstEnumOnlyModule(target))
    }

    def isConstEnumOrConstEnumOnlyModule(s: Symbol): Boolean {
      return isConstEnumSymbol(s) || s.constEnumOnlyModule
    }

    def isReferencedAliasDeclaration(node: Node, checkChildren?: Boolean): Boolean {
      if (isAliasSymbolDeclaration(node)) {
        val symbol = getSymbolOfNode(node)
        if (getSymbolLinks(symbol).referenced) {
          return true
        }
      }

      if (checkChildren) {
        return forEachChild(node, node => isReferencedAliasDeclaration(node, checkChildren))
      }
      return false
    }

    def isImplementationOfOverload(node: FunctionLikeDeclaration) {
      if (nodeIsPresent(node.body)) {
        val symbol = getSymbolOfNode(node)
        val signaturesOfSymbol = getSignaturesOfSymbol(symbol)
        // If this def body corresponds to def with multiple signature, it is implementation of overload
        // e.g.: def foo(a: String): String
        //     def foo(a: Int): Int
        //     def foo(a: any) { // This is implementation of the overloads
        //       return a
        //     }
        return signaturesOfSymbol.length > 1 ||
          // If there is single signature for the symbol, it is overload if that signature isn't coming from the node
          // e.g.: def foo(a: String): String
          //     def foo(a: any) { // This is implementation of the overloads
          //       return a
          //     }
          (signaturesOfSymbol.length == 1 && signaturesOfSymbol[0].declaration != node)
      }
      return false
    }

    def getNodeCheckFlags(node: Node): NodeCheckFlags {
      return getNodeLinks(node).flags
    }

    def getEnumMemberValue(node: EnumMember): Int {
      computeEnumMemberValues(<EnumDeclaration>node.parent)
      return getNodeLinks(node).enumMemberValue
    }

    def getConstantValue(node: EnumMember | PropertyAccessExpression | ElementAccessExpression): Int {
      if (node.kind == SyntaxKind.EnumMember) {
        return getEnumMemberValue(<EnumMember>node)
      }

      val symbol = getNodeLinks(node).resolvedSymbol
      if (symbol && (symbol.flags & SymbolFlags.EnumMember)) {
        // inline property\index accesses only for val enums
        if (isConstEnumDeclaration(symbol.valueDeclaration.parent)) {
          return getEnumMemberValue(<EnumMember>symbol.valueDeclaration)
        }
      }

      return ()
    }

    def isFunctionType(type: Type): Boolean {
      return type.flags & TypeFlags.ObjectType && getSignaturesOfType(type, SignatureKind.Call).length > 0
    }

    def getTypeReferenceSerializationKind(typeName: EntityName): TypeReferenceSerializationKind {
      // Resolve the symbol as a value to ensure the type can be reached at runtime during emit.
      val valueSymbol = resolveEntityName(typeName, SymbolFlags.Value, /*ignoreErrors*/ true)
      val constructorType = valueSymbol ? getTypeOfSymbol(valueSymbol) : ()
      if (constructorType && isConstructorType(constructorType)) {
        return TypeReferenceSerializationKind.TypeWithConstructSignatureAndValue
      }

      // Resolve the symbol as a type so that we can provide a more useful hint for the type serializer.
      val typeSymbol = resolveEntityName(typeName, SymbolFlags.Type, /*ignoreErrors*/ true)
      // We might not be able to resolve type symbol so use unknown type in that case (eg error case)
      if (!typeSymbol) {
        return TypeReferenceSerializationKind.ObjectType
      }
      val type = getDeclaredTypeOfSymbol(typeSymbol)
      if (type == unknownType) {
        return TypeReferenceSerializationKind.Unknown
      }
      else if (type.flags & TypeFlags.Any) {
        return TypeReferenceSerializationKind.ObjectType
      }
      else if (isTypeOfKind(type, TypeFlags.Void)) {
        return TypeReferenceSerializationKind.VoidType
      }
      else if (isTypeOfKind(type, TypeFlags.Boolean)) {
        return TypeReferenceSerializationKind.BooleanType
      }
      else if (isTypeOfKind(type, TypeFlags.NumberLike)) {
        return TypeReferenceSerializationKind.NumberLikeType
      }
      else if (isTypeOfKind(type, TypeFlags.StringLike)) {
        return TypeReferenceSerializationKind.StringLikeType
      }
      else if (isTypeOfKind(type, TypeFlags.Tuple)) {
        return TypeReferenceSerializationKind.ArrayLikeType
      }
      else if (isTypeOfKind(type, TypeFlags.ESSymbol)) {
        return TypeReferenceSerializationKind.ESSymbolType
      }
      else if (isFunctionType(type)) {
        return TypeReferenceSerializationKind.TypeWithCallSignature
      }
      else if (isArrayType(type)) {
        return TypeReferenceSerializationKind.ArrayLikeType
      }
      else {
        return TypeReferenceSerializationKind.ObjectType
      }
    }

    def writeTypeOfDeclaration(declaration: AccessorDeclaration | VariableLikeDeclaration, enclosingDeclaration: Node, flags: TypeFormatFlags, writer: SymbolWriter) {
      // Get type of the symbol if this is the valid symbol otherwise get type at location
      val symbol = getSymbolOfNode(declaration)
      val type = symbol && !(symbol.flags & (SymbolFlags.TypeLiteral | SymbolFlags.Signature))
        ? getTypeOfSymbol(symbol)
        : unknownType

      getSymbolDisplayBuilder().buildTypeDisplay(type, writer, enclosingDeclaration, flags)
    }

    def writeReturnTypeOfSignatureDeclaration(signatureDeclaration: SignatureDeclaration, enclosingDeclaration: Node, flags: TypeFormatFlags, writer: SymbolWriter) {
      val signature = getSignatureFromDeclaration(signatureDeclaration)
      getSymbolDisplayBuilder().buildTypeDisplay(getReturnTypeOfSignature(signature), writer, enclosingDeclaration, flags)
    }

    def writeTypeOfExpression(expr: Expression, enclosingDeclaration: Node, flags: TypeFormatFlags, writer: SymbolWriter) {
      val type = getTypeOfExpression(expr)
      getSymbolDisplayBuilder().buildTypeDisplay(type, writer, enclosingDeclaration, flags)
    }

    def hasGlobalName(name: String): Boolean {
      return hasProperty(globals, name)
    }

    def getReferencedValueSymbol(reference: Identifier): Symbol {
      return getNodeLinks(reference).resolvedSymbol ||
        resolveName(reference, reference.text, SymbolFlags.Value | SymbolFlags.ExportValue | SymbolFlags.Alias,
          /*nodeNotFoundMessage*/ (), /*nameArg*/ ())
    }

    def getReferencedValueDeclaration(reference: Identifier): Declaration {
      Debug.assert(!nodeIsSynthesized(reference))
      val symbol = getReferencedValueSymbol(reference)
      return symbol && getExportSymbolOfValueSymbolIfExported(symbol).valueDeclaration
    }

    def createResolver(): EmitResolver {
      return {
        getReferencedExportContainer,
        getReferencedImportDeclaration,
        getReferencedDeclarationWithCollidingName,
        isDeclarationWithCollidingName,
        isValueAliasDeclaration,
        hasGlobalName,
        isReferencedAliasDeclaration,
        getNodeCheckFlags,
        isTopLevelValueImportEqualsWithEntityName,
        isDeclarationVisible,
        isImplementationOfOverload,
        writeTypeOfDeclaration,
        writeReturnTypeOfSignatureDeclaration,
        writeTypeOfExpression,
        isSymbolAccessible,
        isEntityNameVisible,
        getConstantValue,
        collectLinkedAliases,
        getReferencedValueDeclaration,
        getTypeReferenceSerializationKind,
        isOptionalParameter,
        moduleExportsSomeValue,
        isArgumentsLocalBinding,
        getExternalModuleFileFromDeclaration
      }
    }

    def getExternalModuleFileFromDeclaration(declaration: ImportEqualsDeclaration | ImportDeclaration | ExportDeclaration | ModuleDeclaration): SourceFile {
      val specifier = getExternalModuleName(declaration)
      val moduleSymbol = resolveExternalModuleNameWorker(specifier, specifier, /*moduleNotFoundError*/ ())
      if (!moduleSymbol) {
        return ()
      }
      return getDeclarationOfKind(moduleSymbol, SyntaxKind.SourceFile) as SourceFile
    }

    def initializeTypeChecker() {
      // Bind all source files and propagate errors
      forEach(host.getSourceFiles(), file => {
        bindSourceFile(file, compilerOptions)
      })

      var augmentations: LiteralExpression[][]
      // Initialize global symbol table
      forEach(host.getSourceFiles(), file => {
        if (!isExternalOrCommonJsModule(file)) {
          mergeSymbolTable(globals, file.locals)
        }
        if (file.moduleAugmentations.length) {
          (augmentations || (augmentations = [])).push(file.moduleAugmentations)
        }
      })

      if (augmentations) {
        // merge module augmentations.
        // this needs to be done after global symbol table is initialized to make sure that all ambient modules are indexed
        for (val list of augmentations) {
          for (val augmentation of list) {
            mergeModuleAugmentation(augmentation)
          }
        }
      }

      // Setup global builtins
      addToSymbolTable(globals, builtinGlobals, Diagnostics.Declaration_name_conflicts_with_built_in_global_identifier_0)

      getSymbolLinks(undefinedSymbol).type = undefinedType
      getSymbolLinks(argumentsSymbol).type = getGlobalType("IArguments")
      getSymbolLinks(unknownSymbol).type = unknownType

      // Initialize special types
      globalArrayType = <GenericType>getGlobalType("Array", /*arity*/ 1)
      globalObjectType = getGlobalType("Object")
      globalFunctionType = getGlobalType("Function")
      globalStringType = getGlobalType("String")
      globalNumberType = getGlobalType("Number")
      globalBooleanType = getGlobalType("Boolean")
      globalRegExpType = getGlobalType("RegExp")
      jsxElementType = getExportedTypeFromNamespace("JSX", JsxNames.Element)
      getGlobalClassDecoratorType = memoize(() => getGlobalType("ClassDecorator"))
      getGlobalPropertyDecoratorType = memoize(() => getGlobalType("PropertyDecorator"))
      getGlobalMethodDecoratorType = memoize(() => getGlobalType("MethodDecorator"))
      getGlobalParameterDecoratorType = memoize(() => getGlobalType("ParameterDecorator"))
      getGlobalTypedPropertyDescriptorType = memoize(() => getGlobalType("TypedPropertyDescriptor", /*arity*/ 1))
      getGlobalPromiseType = memoize(() => getGlobalType("Promise", /*arity*/ 1))
      tryGetGlobalPromiseType = memoize(() => getGlobalSymbol("Promise", SymbolFlags.Type, /*diagnostic*/ ()) && getGlobalPromiseType())
      getGlobalPromiseLikeType = memoize(() => getGlobalType("PromiseLike", /*arity*/ 1))
      getInstantiatedGlobalPromiseLikeType = memoize(createInstantiatedPromiseLikeType)
      getGlobalPromiseConstructorSymbol = memoize(() => getGlobalValueSymbol("Promise"))
      getGlobalPromiseConstructorLikeType = memoize(() => getGlobalType("PromiseConstructorLike"))
      getGlobalThenableType = memoize(createThenableType)

      // If we're in ES6 mode, load the TemplateStringsArray.
      // Otherwise, default to 'unknown' for the purposes of type checking in LS scenarios.
      if (languageVersion >= ScriptTarget.ES6) {
        globalTemplateStringsArrayType = getGlobalType("TemplateStringsArray")
        globalESSymbolType = getGlobalType("Symbol")
        globalESSymbolConstructorSymbol = getGlobalValueSymbol("Symbol")
        globalIterableType = <GenericType>getGlobalType("Iterable", /*arity*/ 1)
        globalIteratorType = <GenericType>getGlobalType("Iterator", /*arity*/ 1)
        globalIterableIteratorType = <GenericType>getGlobalType("IterableIterator", /*arity*/ 1)
      }
      else {
        globalTemplateStringsArrayType = unknownType

        // Consider putting Symbol trait in lib.d.ts. On the plus side, putting it in lib.d.ts would make it
        // extensible for Polyfilling Symbols. But putting it into lib.d.ts could also break users that have
        // a global Symbol already, particularly if it is a class.
        globalESSymbolType = createAnonymousType((), emptySymbols, emptyArray, emptyArray, (), ())
        globalESSymbolConstructorSymbol = ()
        globalIterableType = emptyGenericType
        globalIteratorType = emptyGenericType
        globalIterableIteratorType = emptyGenericType
      }

      anyArrayType = createArrayType(anyType)

      val symbol = getGlobalSymbol("ReadonlyArray", SymbolFlags.Type, /*diagnostic*/ ())
      globalReadonlyArrayType = symbol && <GenericType>getTypeOfGlobalSymbol(symbol, /*arity*/ 1)
      anyReadonlyArrayType = globalReadonlyArrayType ? createTypeFromGenericGlobalType(globalReadonlyArrayType, [anyType]) : anyArrayType
    }

    def createInstantiatedPromiseLikeType(): ObjectType {
      val promiseLikeType = getGlobalPromiseLikeType()
      if (promiseLikeType != emptyGenericType) {
        return createTypeReference(<GenericType>promiseLikeType, [anyType])
      }

      return emptyObjectType
    }

    def createThenableType() {
      // build the thenable type that is used to verify against a non-promise "thenable" operand to `await`.
      val thenPropertySymbol = createSymbol(SymbolFlags.Transient | SymbolFlags.Property, "then")
      getSymbolLinks(thenPropertySymbol).type = globalFunctionType

      val thenableType = <ResolvedType>createObjectType(TypeFlags.Anonymous)
      thenableType.properties = [thenPropertySymbol]
      thenableType.members = createSymbolTable(thenableType.properties)
      thenableType.callSignatures = []
      thenableType.constructSignatures = []
      return thenableType
    }

    // GRAMMAR CHECKING
    def checkGrammarDecorators(node: Node): Boolean {
      if (!node.decorators) {
        return false
      }
      if (!nodeCanBeDecorated(node)) {
        if (node.kind == SyntaxKind.MethodDeclaration && !ts.nodeIsPresent((<MethodDeclaration>node).body)) {
          return grammarErrorOnFirstToken(node, Diagnostics.A_decorator_can_only_decorate_a_method_implementation_not_an_overload)
        }
        else {
          return grammarErrorOnFirstToken(node, Diagnostics.Decorators_are_not_valid_here)
        }
      }
      else if (node.kind == SyntaxKind.GetAccessor || node.kind == SyntaxKind.SetAccessor) {
        val accessors = getAllAccessorDeclarations((<ClassDeclaration>node.parent).members, <AccessorDeclaration>node)
        if (accessors.firstAccessor.decorators && node == accessors.secondAccessor) {
          return grammarErrorOnFirstToken(node, Diagnostics.Decorators_cannot_be_applied_to_multiple_get_Slashset_accessors_of_the_same_name)
        }
      }
      return false
    }

    def checkGrammarModifiers(node: Node): Boolean {
      switch (node.kind) {
        case SyntaxKind.GetAccessor:
        case SyntaxKind.SetAccessor:
        case SyntaxKind.Constructor:
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.MethodSignature:
        case SyntaxKind.IndexSignature:
        case SyntaxKind.ModuleDeclaration:
        case SyntaxKind.ImportDeclaration:
        case SyntaxKind.ImportEqualsDeclaration:
        case SyntaxKind.ExportDeclaration:
        case SyntaxKind.ExportAssignment:
        case SyntaxKind.Parameter:
          break
        case SyntaxKind.FunctionDeclaration:
          if (node.modifiers && (node.modifiers.length > 1 || node.modifiers[0].kind != SyntaxKind.AsyncKeyword) &&
            node.parent.kind != SyntaxKind.ModuleBlock && node.parent.kind != SyntaxKind.SourceFile) {
            return grammarErrorOnFirstToken(node, Diagnostics.Modifiers_cannot_appear_here)
          }
          break
        case SyntaxKind.ClassDeclaration:
        case SyntaxKind.InterfaceDeclaration:
        case SyntaxKind.VariableStatement:
        case SyntaxKind.TypeAliasDeclaration:
          if (node.modifiers && node.parent.kind != SyntaxKind.ModuleBlock && node.parent.kind != SyntaxKind.SourceFile) {
            return grammarErrorOnFirstToken(node, Diagnostics.Modifiers_cannot_appear_here)
          }
          break
        case SyntaxKind.EnumDeclaration:
          if (node.modifiers && (node.modifiers.length > 1 || node.modifiers[0].kind != SyntaxKind.ConstKeyword) &&
            node.parent.kind != SyntaxKind.ModuleBlock && node.parent.kind != SyntaxKind.SourceFile) {
            return grammarErrorOnFirstToken(node, Diagnostics.Modifiers_cannot_appear_here)
          }
          break
        default:
          return false
      }

      if (!node.modifiers) {
        return
      }

      var lastStatic: Node, lastPrivate: Node, lastProtected: Node, lastDeclare: Node, lastAsync: Node, lastReadonly: Node
      var flags = 0
      for (val modifier of node.modifiers) {
        if (modifier.kind != SyntaxKind.ReadonlyKeyword) {
          if (node.kind == SyntaxKind.PropertySignature || node.kind == SyntaxKind.MethodSignature) {
            return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_a_type_member, tokenToString(modifier.kind))
          }
          if (node.kind == SyntaxKind.IndexSignature) {
            return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_an_index_signature, tokenToString(modifier.kind))
          }
        }
        switch (modifier.kind) {
          case SyntaxKind.ConstKeyword:
            if (node.kind != SyntaxKind.EnumDeclaration && node.parent.kind == SyntaxKind.ClassDeclaration) {
              return grammarErrorOnNode(node, Diagnostics.A_class_member_cannot_have_the_0_keyword, tokenToString(SyntaxKind.ConstKeyword))
            }
            break
          case SyntaxKind.PublicKeyword:
          case SyntaxKind.ProtectedKeyword:
          case SyntaxKind.PrivateKeyword:
            var text: String
            if (modifier.kind == SyntaxKind.PublicKeyword) {
              text = "public"
            }
            else if (modifier.kind == SyntaxKind.ProtectedKeyword) {
              text = "protected"
              lastProtected = modifier
            }
            else {
              text = "private"
              lastPrivate = modifier
            }

            if (flags & NodeFlags.AccessibilityModifier) {
              return grammarErrorOnNode(modifier, Diagnostics.Accessibility_modifier_already_seen)
            }
            else if (flags & NodeFlags.Static) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_must_precede_1_modifier, text, "static")
            }
            else if (flags & NodeFlags.Readonly) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_must_precede_1_modifier, text, "readonly")
            }
            else if (flags & NodeFlags.Async) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_must_precede_1_modifier, text, "async")
            }
            else if (node.parent.kind == SyntaxKind.ModuleBlock || node.parent.kind == SyntaxKind.SourceFile) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_a_module_or_namespace_element, text)
            }
            else if (flags & NodeFlags.Abstract) {
              if (modifier.kind == SyntaxKind.PrivateKeyword) {
                return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_be_used_with_1_modifier, text, "abstract")
              }
              else {
                return grammarErrorOnNode(modifier, Diagnostics._0_modifier_must_precede_1_modifier, text, "abstract")
              }
            }
            flags |= modifierToFlag(modifier.kind)
            break

          case SyntaxKind.StaticKeyword:
            if (flags & NodeFlags.Static) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_already_seen, "static")
            }
            else if (flags & NodeFlags.Readonly) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_must_precede_1_modifier, "static", "readonly")
            }
            else if (flags & NodeFlags.Async) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_must_precede_1_modifier, "static", "async")
            }
            else if (node.parent.kind == SyntaxKind.ModuleBlock || node.parent.kind == SyntaxKind.SourceFile) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_a_module_or_namespace_element, "static")
            }
            else if (node.kind == SyntaxKind.Parameter) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_a_parameter, "static")
            }
            else if (flags & NodeFlags.Abstract) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_be_used_with_1_modifier, "static", "abstract")
            }
            flags |= NodeFlags.Static
            lastStatic = modifier
            break

          case SyntaxKind.ReadonlyKeyword:
            if (flags & NodeFlags.Readonly) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_already_seen, "readonly")
            }
            else if (node.kind != SyntaxKind.PropertyDeclaration && node.kind != SyntaxKind.PropertySignature && node.kind != SyntaxKind.IndexSignature) {
              return grammarErrorOnNode(modifier, Diagnostics.readonly_modifier_can_only_appear_on_a_property_declaration_or_index_signature)
            }
            flags |= NodeFlags.Readonly
            lastReadonly = modifier
            break

          case SyntaxKind.ExportKeyword:
            if (flags & NodeFlags.Export) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_already_seen, "export")
            }
            else if (flags & NodeFlags.Ambient) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_must_precede_1_modifier, "export", "declare")
            }
            else if (flags & NodeFlags.Abstract) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_must_precede_1_modifier, "export", "abstract")
            }
            else if (flags & NodeFlags.Async) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_must_precede_1_modifier, "export", "async")
            }
            else if (node.parent.kind == SyntaxKind.ClassDeclaration) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_a_class_element, "export")
            }
            else if (node.kind == SyntaxKind.Parameter) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_a_parameter, "export")
            }
            flags |= NodeFlags.Export
            break

          case SyntaxKind.DeclareKeyword:
            if (flags & NodeFlags.Ambient) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_already_seen, "declare")
            }
            else if (flags & NodeFlags.Async) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_be_used_in_an_ambient_context, "async")
            }
            else if (node.parent.kind == SyntaxKind.ClassDeclaration) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_a_class_element, "declare")
            }
            else if (node.kind == SyntaxKind.Parameter) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_a_parameter, "declare")
            }
            else if (isInAmbientContext(node.parent) && node.parent.kind == SyntaxKind.ModuleBlock) {
              return grammarErrorOnNode(modifier, Diagnostics.A_declare_modifier_cannot_be_used_in_an_already_ambient_context)
            }
            flags |= NodeFlags.Ambient
            lastDeclare = modifier
            break

          case SyntaxKind.AbstractKeyword:
            if (flags & NodeFlags.Abstract) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_already_seen, "abstract")
            }
            if (node.kind != SyntaxKind.ClassDeclaration) {
              if (node.kind != SyntaxKind.MethodDeclaration) {
                return grammarErrorOnNode(modifier, Diagnostics.abstract_modifier_can_only_appear_on_a_class_or_method_declaration)
              }
              if (!(node.parent.kind == SyntaxKind.ClassDeclaration && node.parent.flags & NodeFlags.Abstract)) {
                return grammarErrorOnNode(modifier, Diagnostics.Abstract_methods_can_only_appear_within_an_abstract_class)
              }
              if (flags & NodeFlags.Static) {
                return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_be_used_with_1_modifier, "static", "abstract")
              }
              if (flags & NodeFlags.Private) {
                return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_be_used_with_1_modifier, "private", "abstract")
              }
            }

            flags |= NodeFlags.Abstract
            break

          case SyntaxKind.AsyncKeyword:
            if (flags & NodeFlags.Async) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_already_seen, "async")
            }
            else if (flags & NodeFlags.Ambient || isInAmbientContext(node.parent)) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_be_used_in_an_ambient_context, "async")
            }
            else if (node.kind == SyntaxKind.Parameter) {
              return grammarErrorOnNode(modifier, Diagnostics._0_modifier_cannot_appear_on_a_parameter, "async")
            }
            flags |= NodeFlags.Async
            lastAsync = modifier
            break
        }
      }

      if (node.kind == SyntaxKind.Constructor) {
        if (flags & NodeFlags.Static) {
          return grammarErrorOnNode(lastStatic, Diagnostics._0_modifier_cannot_appear_on_a_constructor_declaration, "static")
        }
        if (flags & NodeFlags.Abstract) {
          return grammarErrorOnNode(lastStatic, Diagnostics._0_modifier_cannot_appear_on_a_constructor_declaration, "abstract")
        }
        else if (flags & NodeFlags.Protected) {
          return grammarErrorOnNode(lastProtected, Diagnostics._0_modifier_cannot_appear_on_a_constructor_declaration, "protected")
        }
        else if (flags & NodeFlags.Private) {
          return grammarErrorOnNode(lastPrivate, Diagnostics._0_modifier_cannot_appear_on_a_constructor_declaration, "private")
        }
        else if (flags & NodeFlags.Async) {
          return grammarErrorOnNode(lastAsync, Diagnostics._0_modifier_cannot_appear_on_a_constructor_declaration, "async")
        }
        else if (flags & NodeFlags.Readonly) {
          return grammarErrorOnNode(lastReadonly, Diagnostics._0_modifier_cannot_appear_on_a_constructor_declaration, "readonly")
        }
        return
      }
      else if ((node.kind == SyntaxKind.ImportDeclaration || node.kind == SyntaxKind.ImportEqualsDeclaration) && flags & NodeFlags.Ambient) {
        return grammarErrorOnNode(lastDeclare, Diagnostics.A_0_modifier_cannot_be_used_with_an_import_declaration, "declare")
      }
      else if (node.kind == SyntaxKind.Parameter && (flags & NodeFlags.AccessibilityModifier) && isBindingPattern((<ParameterDeclaration>node).name)) {
        return grammarErrorOnNode(node, Diagnostics.A_parameter_property_may_not_be_a_binding_pattern)
      }
      if (flags & NodeFlags.Async) {
        return checkGrammarAsyncModifier(node, lastAsync)
      }
    }

    def checkGrammarAsyncModifier(node: Node, asyncModifier: Node): Boolean {
      if (languageVersion < ScriptTarget.ES6) {
        return grammarErrorOnNode(asyncModifier, Diagnostics.Async_functions_are_only_available_when_targeting_ECMAScript_6_and_higher)
      }

      switch (node.kind) {
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.FunctionDeclaration:
        case SyntaxKind.FunctionExpression:
        case SyntaxKind.ArrowFunction:
          if (!(<FunctionLikeDeclaration>node).asteriskToken) {
            return false
          }
          break
      }

      return grammarErrorOnNode(asyncModifier, Diagnostics._0_modifier_cannot_be_used_here, "async")
    }

    def checkGrammarForDisallowedTrailingComma(list: NodeArray<Node>): Boolean {
      if (list && list.hasTrailingComma) {
        val start = list.end - ",".length
        val end = list.end
        val sourceFile = getSourceFileOfNode(list[0])
        return grammarErrorAtPos(sourceFile, start, end - start, Diagnostics.Trailing_comma_not_allowed)
      }
    }

    def checkGrammarTypeParameterList(node: FunctionLikeDeclaration, typeParameters: NodeArray<TypeParameterDeclaration>, file: SourceFile): Boolean {
      if (checkGrammarForDisallowedTrailingComma(typeParameters)) {
        return true
      }

      if (typeParameters && typeParameters.length == 0) {
        val start = typeParameters.pos - "<".length
        val end = skipTrivia(file.text, typeParameters.end) + ">".length
        return grammarErrorAtPos(file, start, end - start, Diagnostics.Type_parameter_list_cannot_be_empty)
      }
    }

    def checkGrammarParameterList(parameters: NodeArray<ParameterDeclaration>) {
      if (checkGrammarForDisallowedTrailingComma(parameters)) {
        return true
      }

      var seenOptionalParameter = false
      val parameterCount = parameters.length

      for (var i = 0; i < parameterCount; i++) {
        val parameter = parameters[i]
        if (parameter.dotDotDotToken) {
          if (i != (parameterCount - 1)) {
            return grammarErrorOnNode(parameter.dotDotDotToken, Diagnostics.A_rest_parameter_must_be_last_in_a_parameter_list)
          }

          if (isBindingPattern(parameter.name)) {
            return grammarErrorOnNode(parameter.name, Diagnostics.A_rest_element_cannot_contain_a_binding_pattern)
          }

          if (parameter.questionToken) {
            return grammarErrorOnNode(parameter.questionToken, Diagnostics.A_rest_parameter_cannot_be_optional)
          }

          if (parameter.initializer) {
            return grammarErrorOnNode(parameter.name, Diagnostics.A_rest_parameter_cannot_have_an_initializer)
          }
        }
        else if (parameter.questionToken) {
          seenOptionalParameter = true

          if (parameter.initializer) {
            return grammarErrorOnNode(parameter.name, Diagnostics.Parameter_cannot_have_question_mark_and_initializer)
          }
        }
        else if (seenOptionalParameter && !parameter.initializer) {
          return grammarErrorOnNode(parameter.name, Diagnostics.A_required_parameter_cannot_follow_an_optional_parameter)
        }
      }
    }

    def checkGrammarFunctionLikeDeclaration(node: FunctionLikeDeclaration): Boolean {
      // Prevent cascading error by short-circuit
      val file = getSourceFileOfNode(node)
      return checkGrammarDecorators(node) || checkGrammarModifiers(node) || checkGrammarTypeParameterList(node, node.typeParameters, file) ||
        checkGrammarParameterList(node.parameters) || checkGrammarArrowFunction(node, file)
    }

    def checkGrammarArrowFunction(node: FunctionLikeDeclaration, file: SourceFile): Boolean {
      if (node.kind == SyntaxKind.ArrowFunction) {
        val arrowFunction = <ArrowFunction>node
        val startLine = getLineAndCharacterOfPosition(file, arrowFunction.equalsGreaterThanToken.pos).line
        val endLine = getLineAndCharacterOfPosition(file, arrowFunction.equalsGreaterThanToken.end).line
        if (startLine != endLine) {
          return grammarErrorOnNode(arrowFunction.equalsGreaterThanToken, Diagnostics.Line_terminator_not_permitted_before_arrow)
        }
      }
      return false
    }

    def checkGrammarIndexSignatureParameters(node: SignatureDeclaration): Boolean {
      val parameter = node.parameters[0]
      if (node.parameters.length != 1) {
        if (parameter) {
          return grammarErrorOnNode(parameter.name, Diagnostics.An_index_signature_must_have_exactly_one_parameter)
        }
        else {
          return grammarErrorOnNode(node, Diagnostics.An_index_signature_must_have_exactly_one_parameter)
        }
      }
      if (parameter.dotDotDotToken) {
        return grammarErrorOnNode(parameter.dotDotDotToken, Diagnostics.An_index_signature_cannot_have_a_rest_parameter)
      }
      if (parameter.flags & NodeFlags.Modifier) {
        return grammarErrorOnNode(parameter.name, Diagnostics.An_index_signature_parameter_cannot_have_an_accessibility_modifier)
      }
      if (parameter.questionToken) {
        return grammarErrorOnNode(parameter.questionToken, Diagnostics.An_index_signature_parameter_cannot_have_a_question_mark)
      }
      if (parameter.initializer) {
        return grammarErrorOnNode(parameter.name, Diagnostics.An_index_signature_parameter_cannot_have_an_initializer)
      }
      if (!parameter.type) {
        return grammarErrorOnNode(parameter.name, Diagnostics.An_index_signature_parameter_must_have_a_type_annotation)
      }
      if (parameter.type.kind != SyntaxKind.StringKeyword && parameter.type.kind != SyntaxKind.NumberKeyword) {
        return grammarErrorOnNode(parameter.name, Diagnostics.An_index_signature_parameter_type_must_be_string_or_number)
      }
      if (!node.type) {
        return grammarErrorOnNode(node, Diagnostics.An_index_signature_must_have_a_type_annotation)
      }
    }

    def checkGrammarIndexSignature(node: SignatureDeclaration) {
      // Prevent cascading error by short-circuit
      return checkGrammarDecorators(node) || checkGrammarModifiers(node) || checkGrammarIndexSignatureParameters(node)
    }

    def checkGrammarForAtLeastOneTypeArgument(node: Node, typeArguments: NodeArray<TypeNode>): Boolean {
      if (typeArguments && typeArguments.length == 0) {
        val sourceFile = getSourceFileOfNode(node)
        val start = typeArguments.pos - "<".length
        val end = skipTrivia(sourceFile.text, typeArguments.end) + ">".length
        return grammarErrorAtPos(sourceFile, start, end - start, Diagnostics.Type_argument_list_cannot_be_empty)
      }
    }

    def checkGrammarTypeArguments(node: Node, typeArguments: NodeArray<TypeNode>): Boolean {
      return checkGrammarForDisallowedTrailingComma(typeArguments) ||
        checkGrammarForAtLeastOneTypeArgument(node, typeArguments)
    }

    def checkGrammarForOmittedArgument(node: CallExpression, args: NodeArray<Expression>): Boolean {
      if (args) {
        val sourceFile = getSourceFileOfNode(node)
        for (val arg of args) {
          if (arg.kind == SyntaxKind.OmittedExpression) {
            return grammarErrorAtPos(sourceFile, arg.pos, 0, Diagnostics.Argument_expression_expected)
          }
        }
      }
    }

    def checkGrammarArguments(node: CallExpression, args: NodeArray<Expression>): Boolean {
      return checkGrammarForDisallowedTrailingComma(args) ||
        checkGrammarForOmittedArgument(node, args)
    }

    def checkGrammarHeritageClause(node: HeritageClause): Boolean {
      val types = node.types
      if (checkGrammarForDisallowedTrailingComma(types)) {
        return true
      }
      if (types && types.length == 0) {
        val listType = tokenToString(node.token)
        val sourceFile = getSourceFileOfNode(node)
        return grammarErrorAtPos(sourceFile, types.pos, 0, Diagnostics._0_list_cannot_be_empty, listType)
      }
    }

    def checkGrammarClassDeclarationHeritageClauses(node: ClassLikeDeclaration) {
      var seenExtendsClause = false
      var seenImplementsClause = false

      if (!checkGrammarDecorators(node) && !checkGrammarModifiers(node) && node.heritageClauses) {
        for (val heritageClause of node.heritageClauses) {
          if (heritageClause.token == SyntaxKind.ExtendsKeyword) {
            if (seenExtendsClause) {
              return grammarErrorOnFirstToken(heritageClause, Diagnostics.extends_clause_already_seen)
            }

            if (seenImplementsClause) {
              return grammarErrorOnFirstToken(heritageClause, Diagnostics.extends_clause_must_precede_implements_clause)
            }

            if (heritageClause.types.length > 1) {
              return grammarErrorOnFirstToken(heritageClause.types[1], Diagnostics.Classes_can_only_extend_a_single_class)
            }

            seenExtendsClause = true
          }
          else {
            Debug.assert(heritageClause.token == SyntaxKind.ImplementsKeyword)
            if (seenImplementsClause) {
              return grammarErrorOnFirstToken(heritageClause, Diagnostics.implements_clause_already_seen)
            }

            seenImplementsClause = true
          }

          // Grammar checking heritageClause inside class declaration
          checkGrammarHeritageClause(heritageClause)
        }
      }
    }

    def checkGrammarInterfaceDeclaration(node: InterfaceDeclaration) {
      var seenExtendsClause = false

      if (node.heritageClauses) {
        for (val heritageClause of node.heritageClauses) {
          if (heritageClause.token == SyntaxKind.ExtendsKeyword) {
            if (seenExtendsClause) {
              return grammarErrorOnFirstToken(heritageClause, Diagnostics.extends_clause_already_seen)
            }

            seenExtendsClause = true
          }
          else {
            Debug.assert(heritageClause.token == SyntaxKind.ImplementsKeyword)
            return grammarErrorOnFirstToken(heritageClause, Diagnostics.Interface_declaration_cannot_have_implements_clause)
          }

          // Grammar checking heritageClause inside class declaration
          checkGrammarHeritageClause(heritageClause)
        }
      }

      return false
    }

    def checkGrammarComputedPropertyName(node: Node): Boolean {
      // If node is not a computedPropertyName, just skip the grammar checking
      if (node.kind != SyntaxKind.ComputedPropertyName) {
        return false
      }

      val computedPropertyName = <ComputedPropertyName>node
      if (computedPropertyName.expression.kind == SyntaxKind.BinaryExpression && (<BinaryExpression>computedPropertyName.expression).operatorToken.kind == SyntaxKind.CommaToken) {
        return grammarErrorOnNode(computedPropertyName.expression, Diagnostics.A_comma_expression_is_not_allowed_in_a_computed_property_name)
      }
    }

    def checkGrammarForGenerator(node: FunctionLikeDeclaration) {
      if (node.asteriskToken) {
        Debug.assert(
          node.kind == SyntaxKind.FunctionDeclaration ||
          node.kind == SyntaxKind.FunctionExpression ||
          node.kind == SyntaxKind.MethodDeclaration)
        if (isInAmbientContext(node)) {
          return grammarErrorOnNode(node.asteriskToken, Diagnostics.Generators_are_not_allowed_in_an_ambient_context)
        }
        if (!node.body) {
          return grammarErrorOnNode(node.asteriskToken, Diagnostics.An_overload_signature_cannot_be_declared_as_a_generator)
        }
        if (languageVersion < ScriptTarget.ES6) {
          return grammarErrorOnNode(node.asteriskToken, Diagnostics.Generators_are_only_available_when_targeting_ECMAScript_6_or_higher)
        }
      }
    }

    def checkGrammarForInvalidQuestionMark(node: Declaration, questionToken: Node, message: DiagnosticMessage): Boolean {
      if (questionToken) {
        return grammarErrorOnNode(questionToken, message)
      }
    }

    def checkGrammarObjectLiteralExpression(node: ObjectLiteralExpression, inDestructuring: Boolean) {
      val seen: Map<SymbolFlags> = {}
      val Property = 1
      val GetAccessor = 2
      val SetAccessor = 4
      val GetOrSetAccessor = GetAccessor | SetAccessor

      for (val prop of node.properties) {
        val name = prop.name
        if (prop.kind == SyntaxKind.OmittedExpression ||
          name.kind == SyntaxKind.ComputedPropertyName) {
          // If the name is not a ComputedPropertyName, the grammar checking will skip it
          checkGrammarComputedPropertyName(<ComputedPropertyName>name)
          continue
        }

        if (prop.kind == SyntaxKind.ShorthandPropertyAssignment && !inDestructuring && (<ShorthandPropertyAssignment>prop).objectAssignmentInitializer) {
          // having objectAssignmentInitializer is only valid in ObjectAssignmentPattern
          // outside of destructuring it is a syntax error
          return grammarErrorOnNode((<ShorthandPropertyAssignment>prop).equalsToken, Diagnostics.can_only_be_used_in_an_object_literal_property_inside_a_destructuring_assignment)
        }

        // Modifiers are never allowed on properties except for 'async' on a method declaration
        forEach(prop.modifiers, mod => {
          if (mod.kind != SyntaxKind.AsyncKeyword || prop.kind != SyntaxKind.MethodDeclaration) {
            grammarErrorOnNode(mod, Diagnostics._0_modifier_cannot_be_used_here, getTextOfNode(mod))
          }
        })

        // ECMA-262 11.1.5 Object Initializer
        // If previous is not () then throw a SyntaxError exception if any of the following conditions are true
        // a.This production is contained in strict code and IsDataDescriptor(previous) is true and
        // IsDataDescriptor(propId.descriptor) is true.
        //  b.IsDataDescriptor(previous) is true and IsAccessorDescriptor(propId.descriptor) is true.
        //  c.IsAccessorDescriptor(previous) is true and IsDataDescriptor(propId.descriptor) is true.
        //  d.IsAccessorDescriptor(previous) is true and IsAccessorDescriptor(propId.descriptor) is true
        // and either both previous and propId.descriptor have[[Get]] fields or both previous and propId.descriptor have[[Set]] fields
        var currentKind: Int
        if (prop.kind == SyntaxKind.PropertyAssignment || prop.kind == SyntaxKind.ShorthandPropertyAssignment) {
          // Grammar checking for computedPropertyName and shorthandPropertyAssignment
          checkGrammarForInvalidQuestionMark(prop, (<PropertyAssignment>prop).questionToken, Diagnostics.An_object_member_cannot_be_declared_optional)
          if (name.kind == SyntaxKind.NumericLiteral) {
            checkGrammarNumericLiteral(<LiteralExpression>name)
          }
          currentKind = Property
        }
        else if (prop.kind == SyntaxKind.MethodDeclaration) {
          currentKind = Property
        }
        else if (prop.kind == SyntaxKind.GetAccessor) {
          currentKind = GetAccessor
        }
        else if (prop.kind == SyntaxKind.SetAccessor) {
          currentKind = SetAccessor
        }
        else {
          Debug.fail("Unexpected syntax kind:" + prop.kind)
        }

        if (!hasProperty(seen, (<Identifier>name).text)) {
          seen[(<Identifier>name).text] = currentKind
        }
        else {
          val existingKind = seen[(<Identifier>name).text]
          if (currentKind == Property && existingKind == Property) {
            continue
          }
          else if ((currentKind & GetOrSetAccessor) && (existingKind & GetOrSetAccessor)) {
            if (existingKind != GetOrSetAccessor && currentKind != existingKind) {
              seen[(<Identifier>name).text] = currentKind | existingKind
            }
            else {
              return grammarErrorOnNode(name, Diagnostics.An_object_literal_cannot_have_multiple_get_Slashset_accessors_with_the_same_name)
            }
          }
          else {
            return grammarErrorOnNode(name, Diagnostics.An_object_literal_cannot_have_property_and_accessor_with_the_same_name)
          }
        }
      }
    }

    def checkGrammarJsxElement(node: JsxOpeningLikeElement) {
      val seen: Map<Boolean> = {}
      for (val attr of node.attributes) {
        if (attr.kind == SyntaxKind.JsxSpreadAttribute) {
          continue
        }

        val jsxAttr = (<JsxAttribute>attr)
        val name = jsxAttr.name
        if (!hasProperty(seen, name.text)) {
          seen[name.text] = true
        }
        else {
          return grammarErrorOnNode(name, Diagnostics.JSX_elements_cannot_have_multiple_attributes_with_the_same_name)
        }

        val initializer = jsxAttr.initializer
        if (initializer && initializer.kind == SyntaxKind.JsxExpression && !(<JsxExpression>initializer).expression) {
          return grammarErrorOnNode(jsxAttr.initializer, Diagnostics.JSX_attributes_must_only_be_assigned_a_non_empty_expression)
        }
      }
    }

    def checkGrammarForInOrForOfStatement(forInOrOfStatement: ForInStatement | ForOfStatement): Boolean {
      if (checkGrammarStatementInAmbientContext(forInOrOfStatement)) {
        return true
      }

      if (forInOrOfStatement.initializer.kind == SyntaxKind.VariableDeclarationList) {
        val variableList = <VariableDeclarationList>forInOrOfStatement.initializer
        if (!checkGrammarVariableDeclarationList(variableList)) {
          val declarations = variableList.declarations

          // declarations.length can be zero if there is an error in variable declaration in for-of or for-in
          // See http://www.ecma-international.org/ecma-262/6.0/#sec-for-in-and-for-of-statements for details
          // For example:
          //    var var = 10
          //    for (var of [1,2,3]) {} // this is invalid ES6 syntax
          //    for (var in [1,2,3]) {} // this is invalid ES6 syntax
          // We will then want to skip on grammar checking on variableList declaration
          if (!declarations.length) {
            return false
          }

          if (declarations.length > 1) {
            val diagnostic = forInOrOfStatement.kind == SyntaxKind.ForInStatement
              ? Diagnostics.Only_a_single_variable_declaration_is_allowed_in_a_for_in_statement
              : Diagnostics.Only_a_single_variable_declaration_is_allowed_in_a_for_of_statement
            return grammarErrorOnFirstToken(variableList.declarations[1], diagnostic)
          }
          val firstDeclaration = declarations[0]

          if (firstDeclaration.initializer) {
            val diagnostic = forInOrOfStatement.kind == SyntaxKind.ForInStatement
              ? Diagnostics.The_variable_declaration_of_a_for_in_statement_cannot_have_an_initializer
              : Diagnostics.The_variable_declaration_of_a_for_of_statement_cannot_have_an_initializer
            return grammarErrorOnNode(firstDeclaration.name, diagnostic)
          }
          if (firstDeclaration.type) {
            val diagnostic = forInOrOfStatement.kind == SyntaxKind.ForInStatement
              ? Diagnostics.The_left_hand_side_of_a_for_in_statement_cannot_use_a_type_annotation
              : Diagnostics.The_left_hand_side_of_a_for_of_statement_cannot_use_a_type_annotation
            return grammarErrorOnNode(firstDeclaration, diagnostic)
          }
        }
      }

      return false
    }

    def checkGrammarAccessor(accessor: MethodDeclaration): Boolean {
      val kind = accessor.kind
      if (languageVersion < ScriptTarget.ES5) {
        return grammarErrorOnNode(accessor.name, Diagnostics.Accessors_are_only_available_when_targeting_ECMAScript_5_and_higher)
      }
      else if (isInAmbientContext(accessor)) {
        return grammarErrorOnNode(accessor.name, Diagnostics.An_accessor_cannot_be_declared_in_an_ambient_context)
      }
      else if (accessor.body == ()) {
        return grammarErrorAtPos(getSourceFileOfNode(accessor), accessor.end - 1, ";".length, Diagnostics._0_expected, "{")
      }
      else if (accessor.typeParameters) {
        return grammarErrorOnNode(accessor.name, Diagnostics.An_accessor_cannot_have_type_parameters)
      }
      else if (kind == SyntaxKind.GetAccessor && accessor.parameters.length) {
        return grammarErrorOnNode(accessor.name, Diagnostics.A_get_accessor_cannot_have_parameters)
      }
      else if (kind == SyntaxKind.SetAccessor) {
        if (accessor.type) {
          return grammarErrorOnNode(accessor.name, Diagnostics.A_set_accessor_cannot_have_a_return_type_annotation)
        }
        else if (accessor.parameters.length != 1) {
          return grammarErrorOnNode(accessor.name, Diagnostics.A_set_accessor_must_have_exactly_one_parameter)
        }
        else {
          val parameter = accessor.parameters[0]
          if (parameter.dotDotDotToken) {
            return grammarErrorOnNode(parameter.dotDotDotToken, Diagnostics.A_set_accessor_cannot_have_rest_parameter)
          }
          else if (parameter.flags & NodeFlags.Modifier) {
            return grammarErrorOnNode(accessor.name, Diagnostics.A_parameter_property_is_only_allowed_in_a_constructor_implementation)
          }
          else if (parameter.questionToken) {
            return grammarErrorOnNode(parameter.questionToken, Diagnostics.A_set_accessor_cannot_have_an_optional_parameter)
          }
          else if (parameter.initializer) {
            return grammarErrorOnNode(accessor.name, Diagnostics.A_set_accessor_parameter_cannot_have_an_initializer)
          }
        }
      }
    }

    def checkGrammarForNonSymbolComputedProperty(node: DeclarationName, message: DiagnosticMessage) {
      if (isDynamicName(node)) {
        return grammarErrorOnNode(node, message)
      }
    }

    def checkGrammarMethod(node: MethodDeclaration) {
      if (checkGrammarDisallowedModifiersOnObjectLiteralExpressionMethod(node) ||
        checkGrammarFunctionLikeDeclaration(node) ||
        checkGrammarForGenerator(node)) {
        return true
      }

      if (node.parent.kind == SyntaxKind.ObjectLiteralExpression) {
        if (checkGrammarForInvalidQuestionMark(node, node.questionToken, Diagnostics.A_class_member_cannot_be_declared_optional)) {
          return true
        }
        else if (node.body == ()) {
          return grammarErrorAtPos(getSourceFileOfNode(node), node.end - 1, ";".length, Diagnostics._0_expected, "{")
        }
      }

      if (isClassLike(node.parent)) {
        if (checkGrammarForInvalidQuestionMark(node, node.questionToken, Diagnostics.A_class_member_cannot_be_declared_optional)) {
          return true
        }
        // Technically, computed properties in ambient contexts is disallowed
        // for property declarations and accessors too, not just methods.
        // However, property declarations disallow computed names in general,
        // and accessors are not allowed in ambient contexts in general,
        // so this error only really matters for methods.
        if (isInAmbientContext(node)) {
          return checkGrammarForNonSymbolComputedProperty(node.name, Diagnostics.A_computed_property_name_in_an_ambient_context_must_directly_refer_to_a_built_in_symbol)
        }
        else if (!node.body) {
          return checkGrammarForNonSymbolComputedProperty(node.name, Diagnostics.A_computed_property_name_in_a_method_overload_must_directly_refer_to_a_built_in_symbol)
        }
      }
      else if (node.parent.kind == SyntaxKind.InterfaceDeclaration) {
        return checkGrammarForNonSymbolComputedProperty(node.name, Diagnostics.A_computed_property_name_in_an_interface_must_directly_refer_to_a_built_in_symbol)
      }
      else if (node.parent.kind == SyntaxKind.TypeLiteral) {
        return checkGrammarForNonSymbolComputedProperty(node.name, Diagnostics.A_computed_property_name_in_a_type_literal_must_directly_refer_to_a_built_in_symbol)
      }
    }

    def checkGrammarBreakOrContinueStatement(node: BreakOrContinueStatement): Boolean {
      var current: Node = node
      while (current) {
        if (isFunctionLike(current)) {
          return grammarErrorOnNode(node, Diagnostics.Jump_target_cannot_cross_function_boundary)
        }

        switch (current.kind) {
          case SyntaxKind.LabeledStatement:
            if (node.label && (<LabeledStatement>current).label.text == node.label.text) {
              // found matching label - verify that label usage is correct
              // continue can only target labels that are on iteration statements
              val isMisplacedContinueLabel = node.kind == SyntaxKind.ContinueStatement
                && !isIterationStatement((<LabeledStatement>current).statement, /*lookInLabeledStatement*/ true)

              if (isMisplacedContinueLabel) {
                return grammarErrorOnNode(node, Diagnostics.A_continue_statement_can_only_jump_to_a_label_of_an_enclosing_iteration_statement)
              }

              return false
            }
            break
          case SyntaxKind.SwitchStatement:
            if (node.kind == SyntaxKind.BreakStatement && !node.label) {
              // unlabeled break within switch statement - ok
              return false
            }
            break
          default:
            if (isIterationStatement(current, /*lookInLabeledStatement*/ false) && !node.label) {
              // unlabeled break or continue within iteration statement - ok
              return false
            }
            break
        }

        current = current.parent
      }

      if (node.label) {
        val message = node.kind == SyntaxKind.BreakStatement
          ? Diagnostics.A_break_statement_can_only_jump_to_a_label_of_an_enclosing_statement
          : Diagnostics.A_continue_statement_can_only_jump_to_a_label_of_an_enclosing_iteration_statement

        return grammarErrorOnNode(node, message)
      }
      else {
        val message = node.kind == SyntaxKind.BreakStatement
          ? Diagnostics.A_break_statement_can_only_be_used_within_an_enclosing_iteration_or_switch_statement
          : Diagnostics.A_continue_statement_can_only_be_used_within_an_enclosing_iteration_statement
        return grammarErrorOnNode(node, message)
      }
    }

    def checkGrammarBindingElement(node: BindingElement) {
      if (node.dotDotDotToken) {
        val elements = (<BindingPattern>node.parent).elements
        if (node != lastOrUndefined(elements)) {
          return grammarErrorOnNode(node, Diagnostics.A_rest_element_must_be_last_in_an_array_destructuring_pattern)
        }

        if (node.name.kind == SyntaxKind.ArrayBindingPattern || node.name.kind == SyntaxKind.ObjectBindingPattern) {
          return grammarErrorOnNode(node.name, Diagnostics.A_rest_element_cannot_contain_a_binding_pattern)
        }

        if (node.initializer) {
          // Error on equals token which immediate precedes the initializer
          return grammarErrorAtPos(getSourceFileOfNode(node), node.initializer.pos - 1, 1, Diagnostics.A_rest_element_cannot_have_an_initializer)
        }
      }
    }

    def checkGrammarVariableDeclaration(node: VariableDeclaration) {
      if (node.parent.parent.kind != SyntaxKind.ForInStatement && node.parent.parent.kind != SyntaxKind.ForOfStatement) {
        if (isInAmbientContext(node)) {
          if (node.initializer) {
            // Error on equals token which immediate precedes the initializer
            val equalsTokenLength = "=".length
            return grammarErrorAtPos(getSourceFileOfNode(node), node.initializer.pos - equalsTokenLength,
              equalsTokenLength, Diagnostics.Initializers_are_not_allowed_in_ambient_contexts)
          }
        }
        else if (!node.initializer) {
          if (isBindingPattern(node.name) && !isBindingPattern(node.parent)) {
            return grammarErrorOnNode(node, Diagnostics.A_destructuring_declaration_must_have_an_initializer)
          }
          if (isConst(node)) {
            return grammarErrorOnNode(node, Diagnostics.const_declarations_must_be_initialized)
          }
        }
      }

      val checkLetConstNames = (isLet(node) || isConst(node))

      // 1. LexicalDeclaration : LetOrConst BindingList
      // It is a Syntax Error if the BoundNames of BindingList contains "var".
      // 2. ForDeclaration: ForDeclaration : LetOrConst ForBinding
      // It is a Syntax Error if the BoundNames of ForDeclaration contains "var".

      // It is a SyntaxError if a VariableDeclaration or VariableDeclarationNoIn occurs within strict code
      // and its Identifier is eval or arguments
      return checkLetConstNames && checkGrammarNameInLetOrConstDeclarations(node.name)
    }

    def checkGrammarNameInLetOrConstDeclarations(name: Identifier | BindingPattern): Boolean {
      if (name.kind == SyntaxKind.Identifier) {
        if ((<Identifier>name).originalKeywordKind == SyntaxKind.LetKeyword) {
          return grammarErrorOnNode(name, Diagnostics.let_is_not_allowed_to_be_used_as_a_name_in_let_or_const_declarations)
        }
      }
      else {
        val elements = (<BindingPattern>name).elements
        for (val element of elements) {
          if (element.kind != SyntaxKind.OmittedExpression) {
            checkGrammarNameInLetOrConstDeclarations(element.name)
          }
        }
      }
    }

    def checkGrammarVariableDeclarationList(declarationList: VariableDeclarationList): Boolean {
      val declarations = declarationList.declarations
      if (checkGrammarForDisallowedTrailingComma(declarationList.declarations)) {
        return true
      }

      if (!declarationList.declarations.length) {
        return grammarErrorAtPos(getSourceFileOfNode(declarationList), declarations.pos, declarations.end - declarations.pos, Diagnostics.Variable_declaration_list_cannot_be_empty)
      }
    }

    def allowLetAndConstDeclarations(parent: Node): Boolean {
      switch (parent.kind) {
        case SyntaxKind.IfStatement:
        case SyntaxKind.DoStatement:
        case SyntaxKind.WhileStatement:
        case SyntaxKind.WithStatement:
        case SyntaxKind.ForStatement:
        case SyntaxKind.ForInStatement:
        case SyntaxKind.ForOfStatement:
          return false
        case SyntaxKind.LabeledStatement:
          return allowLetAndConstDeclarations(parent.parent)
      }

      return true
    }

    def checkGrammarForDisallowedLetOrConstStatement(node: VariableStatement) {
      if (!allowLetAndConstDeclarations(node.parent)) {
        if (isLet(node.declarationList)) {
          return grammarErrorOnNode(node, Diagnostics.let_declarations_can_only_be_declared_inside_a_block)
        }
        else if (isConst(node.declarationList)) {
          return grammarErrorOnNode(node, Diagnostics.const_declarations_can_only_be_declared_inside_a_block)
        }
      }
    }

    def hasParseDiagnostics(sourceFile: SourceFile): Boolean {
      return sourceFile.parseDiagnostics.length > 0
    }

    def grammarErrorOnFirstToken(node: Node, message: DiagnosticMessage, arg0?: any, arg1?: any, arg2?: any): Boolean {
      val sourceFile = getSourceFileOfNode(node)
      if (!hasParseDiagnostics(sourceFile)) {
        val span = getSpanOfTokenAtPosition(sourceFile, node.pos)
        diagnostics.add(createFileDiagnostic(sourceFile, span.start, span.length, message, arg0, arg1, arg2))
        return true
      }
    }

    def grammarErrorAtPos(sourceFile: SourceFile, start: Int, length: Int, message: DiagnosticMessage, arg0?: any, arg1?: any, arg2?: any): Boolean {
      if (!hasParseDiagnostics(sourceFile)) {
        diagnostics.add(createFileDiagnostic(sourceFile, start, length, message, arg0, arg1, arg2))
        return true
      }
    }

    def grammarErrorOnNode(node: Node, message: DiagnosticMessage, arg0?: any, arg1?: any, arg2?: any): Boolean {
      val sourceFile = getSourceFileOfNode(node)
      if (!hasParseDiagnostics(sourceFile)) {
        diagnostics.add(createDiagnosticForNode(node, message, arg0, arg1, arg2))
        return true
      }
    }

    def checkGrammarConstructorTypeParameters(node: ConstructorDeclaration) {
      if (node.typeParameters) {
        return grammarErrorAtPos(getSourceFileOfNode(node), node.typeParameters.pos, node.typeParameters.end - node.typeParameters.pos, Diagnostics.Type_parameters_cannot_appear_on_a_constructor_declaration)
      }
    }

    def checkGrammarConstructorTypeAnnotation(node: ConstructorDeclaration) {
      if (node.type) {
        return grammarErrorOnNode(node.type, Diagnostics.Type_annotation_cannot_appear_on_a_constructor_declaration)
      }
    }

    def checkGrammarProperty(node: PropertyDeclaration) {
      if (isClassLike(node.parent)) {
        if (checkGrammarForInvalidQuestionMark(node, node.questionToken, Diagnostics.A_class_member_cannot_be_declared_optional) ||
          checkGrammarForNonSymbolComputedProperty(node.name, Diagnostics.A_computed_property_name_in_a_class_property_declaration_must_directly_refer_to_a_built_in_symbol)) {
          return true
        }
      }
      else if (node.parent.kind == SyntaxKind.InterfaceDeclaration) {
        if (checkGrammarForNonSymbolComputedProperty(node.name, Diagnostics.A_computed_property_name_in_an_interface_must_directly_refer_to_a_built_in_symbol)) {
          return true
        }
        if (node.initializer) {
          return grammarErrorOnNode(node.initializer, Diagnostics.An_interface_property_cannot_have_an_initializer)
        }
      }
      else if (node.parent.kind == SyntaxKind.TypeLiteral) {
        if (checkGrammarForNonSymbolComputedProperty(node.name, Diagnostics.A_computed_property_name_in_a_type_literal_must_directly_refer_to_a_built_in_symbol)) {
          return true
        }
        if (node.initializer) {
          return grammarErrorOnNode(node.initializer, Diagnostics.A_type_literal_property_cannot_have_an_initializer)
        }
      }

      if (isInAmbientContext(node) && node.initializer) {
        return grammarErrorOnFirstToken(node.initializer, Diagnostics.Initializers_are_not_allowed_in_ambient_contexts)
      }
    }

    def checkGrammarTopLevelElementForRequiredDeclareModifier(node: Node): Boolean {
      // A declare modifier is required for any top level .d.ts declaration except export=, default,
      // interfaces and imports categories:
      //
      //  DeclarationElement:
      //   ExportAssignment
      //   export_opt   InterfaceDeclaration
      //   export_opt   TypeAliasDeclaration
      //   export_opt   ImportDeclaration
      //   export_opt   ExternalImportDeclaration
      //   export_opt   AmbientDeclaration
      //
      // TODO: The spec needs to be amended to reflect this grammar.
      if (node.kind == SyntaxKind.InterfaceDeclaration ||
        node.kind == SyntaxKind.TypeAliasDeclaration ||
        node.kind == SyntaxKind.ImportDeclaration ||
        node.kind == SyntaxKind.ImportEqualsDeclaration ||
        node.kind == SyntaxKind.ExportDeclaration ||
        node.kind == SyntaxKind.ExportAssignment ||
        (node.flags & NodeFlags.Ambient) ||
        (node.flags & (NodeFlags.Export | NodeFlags.Default))) {

        return false
      }

      return grammarErrorOnFirstToken(node, Diagnostics.A_declare_modifier_is_required_for_a_top_level_declaration_in_a_d_ts_file)
    }

    def checkGrammarTopLevelElementsForRequiredDeclareModifier(file: SourceFile): Boolean {
      for (val decl of file.statements) {
        if (isDeclaration(decl) || decl.kind == SyntaxKind.VariableStatement) {
          if (checkGrammarTopLevelElementForRequiredDeclareModifier(decl)) {
            return true
          }
        }
      }
    }

    def checkGrammarSourceFile(node: SourceFile): Boolean {
      return isInAmbientContext(node) && checkGrammarTopLevelElementsForRequiredDeclareModifier(node)
    }

    def checkGrammarStatementInAmbientContext(node: Node): Boolean {
      if (isInAmbientContext(node)) {
        // An accessors is already reported about the ambient context
        if (isAccessor(node.parent.kind)) {
          return getNodeLinks(node).hasReportedStatementInAmbientContext = true
        }

        // Find containing block which is either Block, ModuleBlock, SourceFile
        val links = getNodeLinks(node)
        if (!links.hasReportedStatementInAmbientContext && isFunctionLike(node.parent)) {
          return getNodeLinks(node).hasReportedStatementInAmbientContext = grammarErrorOnFirstToken(node, Diagnostics.An_implementation_cannot_be_declared_in_ambient_contexts)
        }

        // We are either parented by another statement, or some sort of block.
        // If we're in a block, we only want to really report an error once
        // to prevent noisiness.  So use a bit on the block to indicate if
        // this has already been reported, and don't report if it has.
        //
        if (node.parent.kind == SyntaxKind.Block || node.parent.kind == SyntaxKind.ModuleBlock || node.parent.kind == SyntaxKind.SourceFile) {
          val links = getNodeLinks(node.parent)
          // Check if the containing block ever report this error
          if (!links.hasReportedStatementInAmbientContext) {
            return links.hasReportedStatementInAmbientContext = grammarErrorOnFirstToken(node, Diagnostics.Statements_are_not_allowed_in_ambient_contexts)
          }
        }
        else {
          // We must be parented by a statement.  If so, there's no need
          // to report the error as our parent will have already done it.
          // Debug.assert(isStatement(node.parent))
        }
      }
    }

    def checkGrammarNumericLiteral(node: LiteralExpression): Boolean {
      // Grammar checking
      if (node.isOctalLiteral && languageVersion >= ScriptTarget.ES5) {
        return grammarErrorOnNode(node, Diagnostics.Octal_literals_are_not_available_when_targeting_ECMAScript_5_and_higher)
      }
    }

    def grammarErrorAfterFirstToken(node: Node, message: DiagnosticMessage, arg0?: any, arg1?: any, arg2?: any): Boolean {
      val sourceFile = getSourceFileOfNode(node)
      if (!hasParseDiagnostics(sourceFile)) {
        val span = getSpanOfTokenAtPosition(sourceFile, node.pos)
        diagnostics.add(createFileDiagnostic(sourceFile, textSpanEnd(span), /*length*/ 0, message, arg0, arg1, arg2))
        return true
      }
    }
  }
}
