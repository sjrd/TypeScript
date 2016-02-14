package be.doeraene.tsc

/// <reference path="checker.ts"/>

/* @internal */
object DeclarationEmitter {
  interface ModuleElementDeclarationEmitInfo {
    node: Node
    outputPos: Int
    indent: Int
    asynchronousOutput?: String; // If the output for alias was written asynchronously, the corresponding output
    subModuleElementDeclarationEmitInfo?: ModuleElementDeclarationEmitInfo[]
    isVisible?: Boolean
  }

  interface DeclarationEmit {
    reportedDeclarationError: Boolean
    moduleElementDeclarationEmitInfo: ModuleElementDeclarationEmitInfo[]
    synchronousDeclarationOutput: String
    referencePathsOutput: String
  }

  type GetSymbolAccessibilityDiagnostic = (symbolAccessibilityResult: SymbolAccessibilityResult) => SymbolAccessibilityDiagnostic

  interface EmitTextWriterWithSymbolWriter extends EmitTextWriter, SymbolWriter {
    getSymbolAccessibilityDiagnostic: GetSymbolAccessibilityDiagnostic
  }

  interface SymbolAccessibilityDiagnostic {
    errorNode: Node
    diagnosticMessage: DiagnosticMessage
    typeName?: DeclarationName
  }

  def getDeclarationDiagnostics(host: EmitHost, resolver: EmitResolver, targetSourceFile: SourceFile): Diagnostic[] {
    val declarationDiagnostics = createDiagnosticCollection()
    forEachExpectedEmitFile(host, getDeclarationDiagnosticsFromFile, targetSourceFile)
    return declarationDiagnostics.getDiagnostics(targetSourceFile.fileName)

    def getDeclarationDiagnosticsFromFile({ declarationFilePath }, sources: SourceFile[], isBundledEmit: Boolean) {
      emitDeclarations(host, resolver, declarationDiagnostics, declarationFilePath, sources, isBundledEmit)
    }
  }

  def emitDeclarations(host: EmitHost, resolver: EmitResolver, emitterDiagnostics: DiagnosticCollection, declarationFilePath: String,
    sourceFiles: SourceFile[], isBundledEmit: Boolean): DeclarationEmit {
    val newLine = host.getNewLine()
    val compilerOptions = host.getCompilerOptions()

    var write: (s: String) => void
    var writeLine: () => void
    var increaseIndent: () => void
    var decreaseIndent: () => void
    var writeTextOfNode: (text: String, node: Node) => void

    var writer: EmitTextWriterWithSymbolWriter

    createAndSetNewTextWriterWithSymbolWriter()

    var enclosingDeclaration: Node
    var resultHasExternalModuleIndicator: Boolean
    var currentText: String
    var currentLineMap: Int[]
    var currentIdentifiers: Map<String>
    var isCurrentFileExternalModule: Boolean
    var reportedDeclarationError = false
    var errorNameNode: DeclarationName
    val emitJsDocComments = compilerOptions.removeComments ? def (declaration: Node) { } : writeJsDocComments
    val emit = compilerOptions.stripInternal ? stripInternal : emitNode
    var noDeclare: Boolean

    var moduleElementDeclarationEmitInfo: ModuleElementDeclarationEmitInfo[] = []
    var asynchronousSubModuleDeclarationEmitInfo: ModuleElementDeclarationEmitInfo[]

    // Contains the reference paths that needs to go in the declaration file.
    // Collecting this separately because reference paths need to be first thing in the declaration file
    // and we could be collecting these paths from multiple files into single one with --out option
    var referencePathsOutput = ""

    // Emit references corresponding to each file
    val emittedReferencedFiles: SourceFile[] = []
    var addedGlobalFileReference = false
    var allSourcesModuleElementDeclarationEmitInfo: ModuleElementDeclarationEmitInfo[] = []
    forEach(sourceFiles, sourceFile => {
      // Dont emit for javascript file
      if (isSourceFileJavaScript(sourceFile)) {
        return
      }

      // Check what references need to be added
      if (!compilerOptions.noResolve) {
        forEach(sourceFile.referencedFiles, fileReference => {
          val referencedFile = tryResolveScriptReference(host, sourceFile, fileReference)

          // Emit reference in dts, if the file reference was not already emitted
          if (referencedFile && !contains(emittedReferencedFiles, referencedFile)) {
            // Add a reference to generated dts file,
            // global file reference is added only
            //  - if it is not bundled emit (because otherwise it would be self reference)
            //  - and it is not already added
            if (writeReferencePath(referencedFile, !isBundledEmit && !addedGlobalFileReference)) {
              addedGlobalFileReference = true
            }
            emittedReferencedFiles.push(referencedFile)
          }
        })
      }

      resultHasExternalModuleIndicator = false
      if (!isBundledEmit || !isExternalModule(sourceFile)) {
        noDeclare = false
        emitSourceFile(sourceFile)
      }
      else if (isExternalModule(sourceFile)) {
        noDeclare = true
        write(`declare module "${getResolvedExternalModuleName(host, sourceFile)}" {`)
        writeLine()
        increaseIndent()
        emitSourceFile(sourceFile)
        decreaseIndent()
        write("}")
        writeLine()
      }

      // create asynchronous output for the importDeclarations
      if (moduleElementDeclarationEmitInfo.length) {
        val oldWriter = writer
        forEach(moduleElementDeclarationEmitInfo, aliasEmitInfo => {
          if (aliasEmitInfo.isVisible && !aliasEmitInfo.asynchronousOutput) {
            Debug.assert(aliasEmitInfo.node.kind == SyntaxKind.ImportDeclaration)
            createAndSetNewTextWriterWithSymbolWriter()
            Debug.assert(aliasEmitInfo.indent == 0 || (aliasEmitInfo.indent == 1 && isBundledEmit))
            for (var i = 0; i < aliasEmitInfo.indent; i++) {
              increaseIndent()
            }
            writeImportDeclaration(<ImportDeclaration>aliasEmitInfo.node)
            aliasEmitInfo.asynchronousOutput = writer.getText()
            for (var i = 0; i < aliasEmitInfo.indent; i++) {
              decreaseIndent()
            }
          }
        })
        setWriter(oldWriter)

        allSourcesModuleElementDeclarationEmitInfo = allSourcesModuleElementDeclarationEmitInfo.concat(moduleElementDeclarationEmitInfo)
        moduleElementDeclarationEmitInfo = []
      }

      if (!isBundledEmit && isExternalModule(sourceFile) && sourceFile.moduleAugmentations.length && !resultHasExternalModuleIndicator) {
        // if file was external module with augmentations - this fact should be preserved in .d.ts as well.
        // in case if we didn't write any external module specifiers in .d.ts we need to emit something
        // that will force compiler to think that this file is an external module - 'export {}' is a reasonable choice here.
        write("export {};")
        writeLine()
      }
    })

    return {
      reportedDeclarationError,
      moduleElementDeclarationEmitInfo: allSourcesModuleElementDeclarationEmitInfo,
      synchronousDeclarationOutput: writer.getText(),
      referencePathsOutput,
    }

    def hasInternalAnnotation(range: CommentRange) {
      val comment = currentText.substring(range.pos, range.end)
      return comment.indexOf("@internal") >= 0
    }

    def stripInternal(node: Node) {
      if (node) {
        val leadingCommentRanges = getLeadingCommentRanges(currentText, node.pos)
        if (forEach(leadingCommentRanges, hasInternalAnnotation)) {
          return
        }

        emitNode(node)
      }
    }

    def createAndSetNewTextWriterWithSymbolWriter(): void {
      val writer = <EmitTextWriterWithSymbolWriter>createTextWriter(newLine)
      writer.trackSymbol = trackSymbol
      writer.reportInaccessibleThisError = reportInaccessibleThisError
      writer.writeKeyword = writer.write
      writer.writeOperator = writer.write
      writer.writePunctuation = writer.write
      writer.writeSpace = writer.write
      writer.writeStringLiteral = writer.writeLiteral
      writer.writeParameter = writer.write
      writer.writeSymbol = writer.write
      setWriter(writer)
    }

    def setWriter(newWriter: EmitTextWriterWithSymbolWriter) {
      writer = newWriter
      write = newWriter.write
      writeTextOfNode = newWriter.writeTextOfNode
      writeLine = newWriter.writeLine
      increaseIndent = newWriter.increaseIndent
      decreaseIndent = newWriter.decreaseIndent
    }

    def writeAsynchronousModuleElements(nodes: Node[]) {
      val oldWriter = writer
      forEach(nodes, declaration => {
        var nodeToCheck: Node
        if (declaration.kind == SyntaxKind.VariableDeclaration) {
          nodeToCheck = declaration.parent.parent
        }
        else if (declaration.kind == SyntaxKind.NamedImports || declaration.kind == SyntaxKind.ImportSpecifier || declaration.kind == SyntaxKind.ImportClause) {
          Debug.fail("We should be getting ImportDeclaration instead to write")
        }
        else {
          nodeToCheck = declaration
        }

        var moduleElementEmitInfo = forEach(moduleElementDeclarationEmitInfo, declEmitInfo => declEmitInfo.node == nodeToCheck ? declEmitInfo : undefined)
        if (!moduleElementEmitInfo && asynchronousSubModuleDeclarationEmitInfo) {
          moduleElementEmitInfo = forEach(asynchronousSubModuleDeclarationEmitInfo, declEmitInfo => declEmitInfo.node == nodeToCheck ? declEmitInfo : undefined)
        }

        // If the alias was marked as not visible when we saw its declaration, we would have saved the aliasEmitInfo, but if we haven't yet visited the alias declaration
        // then we don't need to write it at this point. We will write it when we actually see its declaration
        // Eg.
        // def bar(a: foo.Foo) { }
        // import foo = require("foo")
        // Writing of def bar would mark alias declaration foo as visible but we haven't yet visited that declaration so do nothing,
        // we would write alias foo declaration when we visit it since it would now be marked as visible
        if (moduleElementEmitInfo) {
          if (moduleElementEmitInfo.node.kind == SyntaxKind.ImportDeclaration) {
            // we have to create asynchronous output only after we have collected complete information
            // because it is possible to enable multiple bindings as asynchronously visible
            moduleElementEmitInfo.isVisible = true
          }
          else {
            createAndSetNewTextWriterWithSymbolWriter()
            for (var declarationIndent = moduleElementEmitInfo.indent; declarationIndent; declarationIndent--) {
              increaseIndent()
            }

            if (nodeToCheck.kind == SyntaxKind.ModuleDeclaration) {
              Debug.assert(asynchronousSubModuleDeclarationEmitInfo == undefined)
              asynchronousSubModuleDeclarationEmitInfo = []
            }
            writeModuleElement(nodeToCheck)
            if (nodeToCheck.kind == SyntaxKind.ModuleDeclaration) {
              moduleElementEmitInfo.subModuleElementDeclarationEmitInfo = asynchronousSubModuleDeclarationEmitInfo
              asynchronousSubModuleDeclarationEmitInfo = undefined
            }
            moduleElementEmitInfo.asynchronousOutput = writer.getText()
          }
        }
      })
      setWriter(oldWriter)
    }

    def handleSymbolAccessibilityError(symbolAccessibilityResult: SymbolAccessibilityResult) {
      if (symbolAccessibilityResult.accessibility == SymbolAccessibility.Accessible) {
        // write the aliases
        if (symbolAccessibilityResult && symbolAccessibilityResult.aliasesToMakeVisible) {
          writeAsynchronousModuleElements(symbolAccessibilityResult.aliasesToMakeVisible)
        }
      }
      else {
        // Report error
        reportedDeclarationError = true
        val errorInfo = writer.getSymbolAccessibilityDiagnostic(symbolAccessibilityResult)
        if (errorInfo) {
          if (errorInfo.typeName) {
            emitterDiagnostics.add(createDiagnosticForNode(symbolAccessibilityResult.errorNode || errorInfo.errorNode,
              errorInfo.diagnosticMessage,
              getTextOfNodeFromSourceText(currentText, errorInfo.typeName),
              symbolAccessibilityResult.errorSymbolName,
              symbolAccessibilityResult.errorModuleName))
          }
          else {
            emitterDiagnostics.add(createDiagnosticForNode(symbolAccessibilityResult.errorNode || errorInfo.errorNode,
              errorInfo.diagnosticMessage,
              symbolAccessibilityResult.errorSymbolName,
              symbolAccessibilityResult.errorModuleName))
          }
        }
      }
    }

    def trackSymbol(symbol: Symbol, enclosingDeclaration?: Node, meaning?: SymbolFlags) {
      handleSymbolAccessibilityError(resolver.isSymbolAccessible(symbol, enclosingDeclaration, meaning))
    }

    def reportInaccessibleThisError() {
      if (errorNameNode) {
        reportedDeclarationError = true
        emitterDiagnostics.add(createDiagnosticForNode(errorNameNode, Diagnostics.The_inferred_type_of_0_references_an_inaccessible_this_type_A_type_annotation_is_necessary,
          declarationNameToString(errorNameNode)))
      }
    }

    def writeTypeOfDeclaration(declaration: AccessorDeclaration | VariableLikeDeclaration, type: TypeNode, getSymbolAccessibilityDiagnostic: GetSymbolAccessibilityDiagnostic) {
      writer.getSymbolAccessibilityDiagnostic = getSymbolAccessibilityDiagnostic
      write(": ")
      if (type) {
        // Write the type
        emitType(type)
      }
      else {
        errorNameNode = declaration.name
        resolver.writeTypeOfDeclaration(declaration, enclosingDeclaration, TypeFormatFlags.UseTypeOfFunction, writer)
        errorNameNode = undefined
      }
    }

    def writeReturnTypeAtSignature(signature: SignatureDeclaration, getSymbolAccessibilityDiagnostic: GetSymbolAccessibilityDiagnostic) {
      writer.getSymbolAccessibilityDiagnostic = getSymbolAccessibilityDiagnostic
      write(": ")
      if (signature.type) {
        // Write the type
        emitType(signature.type)
      }
      else {
        errorNameNode = signature.name
        resolver.writeReturnTypeOfSignatureDeclaration(signature, enclosingDeclaration, TypeFormatFlags.UseTypeOfFunction, writer)
        errorNameNode = undefined
      }
    }

    def emitLines(nodes: Node[]) {
      for (val node of nodes) {
        emit(node)
      }
    }

    def emitSeparatedList(nodes: Node[], separator: String, eachNodeEmitFn: (node: Node) => void, canEmitFn?: (node: Node) => Boolean) {
      var currentWriterPos = writer.getTextPos()
      for (val node of nodes) {
        if (!canEmitFn || canEmitFn(node)) {
          if (currentWriterPos != writer.getTextPos()) {
            write(separator)
          }
          currentWriterPos = writer.getTextPos()
          eachNodeEmitFn(node)
        }
      }
    }

    def emitCommaList(nodes: Node[], eachNodeEmitFn: (node: Node) => void, canEmitFn?: (node: Node) => Boolean) {
      emitSeparatedList(nodes, ", ", eachNodeEmitFn, canEmitFn)
    }

    def writeJsDocComments(declaration: Node) {
      if (declaration) {
        val jsDocComments = getJsDocCommentsFromText(declaration, currentText)
        emitNewLineBeforeLeadingComments(currentLineMap, writer, declaration, jsDocComments)
        // jsDoc comments are emitted at /*leading comment1 */space/*leading comment*/space
        emitComments(currentText, currentLineMap, writer, jsDocComments, /*trailingSeparator*/ true, newLine, writeCommentRange)
      }
    }

    def emitTypeWithNewGetSymbolAccessibilityDiagnostic(type: TypeNode | EntityName, getSymbolAccessibilityDiagnostic: GetSymbolAccessibilityDiagnostic) {
      writer.getSymbolAccessibilityDiagnostic = getSymbolAccessibilityDiagnostic
      emitType(type)
    }

    def emitType(type: TypeNode | Identifier | QualifiedName) {
      switch (type.kind) {
        case SyntaxKind.AnyKeyword:
        case SyntaxKind.StringKeyword:
        case SyntaxKind.NumberKeyword:
        case SyntaxKind.BooleanKeyword:
        case SyntaxKind.SymbolKeyword:
        case SyntaxKind.VoidKeyword:
        case SyntaxKind.ThisType:
        case SyntaxKind.StringLiteralType:
          return writeTextOfNode(currentText, type)
        case SyntaxKind.ExpressionWithTypeArguments:
          return emitExpressionWithTypeArguments(<ExpressionWithTypeArguments>type)
        case SyntaxKind.TypeReference:
          return emitTypeReference(<TypeReferenceNode>type)
        case SyntaxKind.TypeQuery:
          return emitTypeQuery(<TypeQueryNode>type)
        case SyntaxKind.ArrayType:
          return emitArrayType(<ArrayTypeNode>type)
        case SyntaxKind.TupleType:
          return emitTupleType(<TupleTypeNode>type)
        case SyntaxKind.UnionType:
          return emitUnionType(<UnionTypeNode>type)
        case SyntaxKind.IntersectionType:
          return emitIntersectionType(<IntersectionTypeNode>type)
        case SyntaxKind.ParenthesizedType:
          return emitParenType(<ParenthesizedTypeNode>type)
        case SyntaxKind.FunctionType:
        case SyntaxKind.ConstructorType:
          return emitSignatureDeclarationWithJsDocComments(<FunctionOrConstructorTypeNode>type)
        case SyntaxKind.TypeLiteral:
          return emitTypeLiteral(<TypeLiteralNode>type)
        case SyntaxKind.Identifier:
          return emitEntityName(<Identifier>type)
        case SyntaxKind.QualifiedName:
          return emitEntityName(<QualifiedName>type)
        case SyntaxKind.TypePredicate:
          return emitTypePredicate(<TypePredicateNode>type)
      }

      def writeEntityName(entityName: EntityName | Expression) {
        if (entityName.kind == SyntaxKind.Identifier) {
          writeTextOfNode(currentText, entityName)
        }
        else {
          val left = entityName.kind == SyntaxKind.QualifiedName ? (<QualifiedName>entityName).left : (<PropertyAccessExpression>entityName).expression
          val right = entityName.kind == SyntaxKind.QualifiedName ? (<QualifiedName>entityName).right : (<PropertyAccessExpression>entityName).name
          writeEntityName(left)
          write(".")
          writeTextOfNode(currentText, right)
        }
      }

      def emitEntityName(entityName: EntityName | PropertyAccessExpression) {
        val visibilityResult = resolver.isEntityNameVisible(entityName,
          // Aliases can be written asynchronously so use correct enclosing declaration
          entityName.parent.kind == SyntaxKind.ImportEqualsDeclaration ? entityName.parent : enclosingDeclaration)

        handleSymbolAccessibilityError(visibilityResult)
        writeEntityName(entityName)
      }

      def emitExpressionWithTypeArguments(node: ExpressionWithTypeArguments) {
        if (isSupportedExpressionWithTypeArguments(node)) {
          Debug.assert(node.expression.kind == SyntaxKind.Identifier || node.expression.kind == SyntaxKind.PropertyAccessExpression)
          emitEntityName(<Identifier | PropertyAccessExpression>node.expression)
          if (node.typeArguments) {
            write("<")
            emitCommaList(node.typeArguments, emitType)
            write(">")
          }
        }
      }

      def emitTypeReference(type: TypeReferenceNode) {
        emitEntityName(type.typeName)
        if (type.typeArguments) {
          write("<")
          emitCommaList(type.typeArguments, emitType)
          write(">")
        }
      }

      def emitTypePredicate(type: TypePredicateNode) {
        writeTextOfNode(currentText, type.parameterName)
        write(" is ")
        emitType(type.type)
      }

      def emitTypeQuery(type: TypeQueryNode) {
        write("typeof ")
        emitEntityName(type.exprName)
      }

      def emitArrayType(type: ArrayTypeNode) {
        emitType(type.elementType)
        write("[]")
      }

      def emitTupleType(type: TupleTypeNode) {
        write("[")
        emitCommaList(type.elementTypes, emitType)
        write("]")
      }

      def emitUnionType(type: UnionTypeNode) {
        emitSeparatedList(type.types, " | ", emitType)
      }

      def emitIntersectionType(type: IntersectionTypeNode) {
        emitSeparatedList(type.types, " & ", emitType)
      }

      def emitParenType(type: ParenthesizedTypeNode) {
        write("(")
        emitType(type.type)
        write(")")
      }

      def emitTypeLiteral(type: TypeLiteralNode) {
        write("{")
        if (type.members.length) {
          writeLine()
          increaseIndent()
          // write members
          emitLines(type.members)
          decreaseIndent()
        }
        write("}")
      }
    }

    def emitSourceFile(node: SourceFile) {
      currentText = node.text
      currentLineMap = getLineStarts(node)
      currentIdentifiers = node.identifiers
      isCurrentFileExternalModule = isExternalModule(node)
      enclosingDeclaration = node
      emitDetachedComments(currentText, currentLineMap, writer, writeCommentRange, node, newLine, true /* remove comments */)
      emitLines(node.statements)
    }

    // Return a temp variable name to be used in `export default` statements.
    // The temp name will be of the form _default_counter.
    // Note that default is only allowed at most once in a module, so we
    // do not need to keep track of created temp names.
    def getExportDefaultTempVariableName(): String {
      val baseName = "_default"
      if (!hasProperty(currentIdentifiers, baseName)) {
        return baseName
      }
      var count = 0
      while (true) {
        count++
        val name = baseName + "_" + count
        if (!hasProperty(currentIdentifiers, name)) {
          return name
        }
      }
    }

    def emitExportAssignment(node: ExportAssignment) {
      if (node.expression.kind == SyntaxKind.Identifier) {
        write(node.isExportEquals ? "export = " : "export default ")
        writeTextOfNode(currentText, node.expression)
      }
      else {
        // Expression
        val tempVarName = getExportDefaultTempVariableName()
        write("declare var ")
        write(tempVarName)
        write(": ")
        writer.getSymbolAccessibilityDiagnostic = getDefaultExportAccessibilityDiagnostic
        resolver.writeTypeOfExpression(node.expression, enclosingDeclaration, TypeFormatFlags.UseTypeOfFunction, writer)
        write(";")
        writeLine()
        write(node.isExportEquals ? "export = " : "export default ")
        write(tempVarName)
      }
      write(";")
      writeLine()

      // Make all the declarations visible for the name
      if (node.expression.kind == SyntaxKind.Identifier) {
        val nodes = resolver.collectLinkedAliases(<Identifier>node.expression)

        // write each of these declarations asynchronously
        writeAsynchronousModuleElements(nodes)
      }

      def getDefaultExportAccessibilityDiagnostic(diagnostic: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
        return {
          diagnosticMessage: Diagnostics.Default_export_of_the_module_has_or_is_using_private_name_0,
          errorNode: node
        }
      }
    }

    def isModuleElementVisible(node: Declaration) {
      return resolver.isDeclarationVisible(node)
    }

    def emitModuleElement(node: Node, isModuleElementVisible: Boolean) {
      if (isModuleElementVisible) {
        writeModuleElement(node)
      }
      // Import equals declaration in internal module can become visible as part of any emit so lets make sure we add these irrespective
      else if (node.kind == SyntaxKind.ImportEqualsDeclaration ||
        (node.parent.kind == SyntaxKind.SourceFile && isCurrentFileExternalModule)) {
        var isVisible: Boolean
        if (asynchronousSubModuleDeclarationEmitInfo && node.parent.kind != SyntaxKind.SourceFile) {
          // Import declaration of another module that is visited async so lets put it in right spot
          asynchronousSubModuleDeclarationEmitInfo.push({
            node,
            outputPos: writer.getTextPos(),
            indent: writer.getIndent(),
            isVisible
          })
        }
        else {
          if (node.kind == SyntaxKind.ImportDeclaration) {
            val importDeclaration = <ImportDeclaration>node
            if (importDeclaration.importClause) {
              isVisible = (importDeclaration.importClause.name && resolver.isDeclarationVisible(importDeclaration.importClause)) ||
              isVisibleNamedBinding(importDeclaration.importClause.namedBindings)
            }
          }
          moduleElementDeclarationEmitInfo.push({
            node,
            outputPos: writer.getTextPos(),
            indent: writer.getIndent(),
            isVisible
          })
        }
      }
    }

    def writeModuleElement(node: Node) {
      switch (node.kind) {
        case SyntaxKind.FunctionDeclaration:
          return writeFunctionDeclaration(<FunctionLikeDeclaration>node)
        case SyntaxKind.VariableStatement:
          return writeVariableStatement(<VariableStatement>node)
        case SyntaxKind.InterfaceDeclaration:
          return writeInterfaceDeclaration(<InterfaceDeclaration>node)
        case SyntaxKind.ClassDeclaration:
          return writeClassDeclaration(<ClassDeclaration>node)
        case SyntaxKind.TypeAliasDeclaration:
          return writeTypeAliasDeclaration(<TypeAliasDeclaration>node)
        case SyntaxKind.EnumDeclaration:
          return writeEnumDeclaration(<EnumDeclaration>node)
        case SyntaxKind.ModuleDeclaration:
          return writeModuleDeclaration(<ModuleDeclaration>node)
        case SyntaxKind.ImportEqualsDeclaration:
          return writeImportEqualsDeclaration(<ImportEqualsDeclaration>node)
        case SyntaxKind.ImportDeclaration:
          return writeImportDeclaration(<ImportDeclaration>node)
        default:
          Debug.fail("Unknown symbol kind")
      }
    }

    def emitModuleElementDeclarationFlags(node: Node) {
      // If the node is parented in the current source file we need to emit declare or just export
      if (node.parent.kind == SyntaxKind.SourceFile) {
        // If the node is exported
        if (node.flags & NodeFlags.Export) {
          write("export ")
        }

        if (node.flags & NodeFlags.Default) {
          write("default ")
        }
        else if (node.kind != SyntaxKind.InterfaceDeclaration && !noDeclare) {
          write("declare ")
        }
      }
    }

    def emitClassMemberDeclarationFlags(flags: NodeFlags) {
      if (flags & NodeFlags.Private) {
        write("private ")
      }
      else if (flags & NodeFlags.Protected) {
        write("protected ")
      }

      if (flags & NodeFlags.Static) {
        write("static ")
      }
      if (flags & NodeFlags.Readonly) {
        write("readonly ")
      }
      if (flags & NodeFlags.Abstract) {
        write("abstract ")
      }
    }

    def writeImportEqualsDeclaration(node: ImportEqualsDeclaration) {
      // note usage of writer. methods instead of aliases created, just to make sure we are using
      // correct writer especially to handle asynchronous alias writing
      emitJsDocComments(node)
      if (node.flags & NodeFlags.Export) {
        write("export ")
      }
      write("import ")
      writeTextOfNode(currentText, node.name)
      write(" = ")
      if (isInternalModuleImportEqualsDeclaration(node)) {
        emitTypeWithNewGetSymbolAccessibilityDiagnostic(<EntityName>node.moduleReference, getImportEntityNameVisibilityError)
        write(";")
      }
      else {
        write("require(")
        emitExternalModuleSpecifier(node)
        write(");")
      }
      writer.writeLine()

      def getImportEntityNameVisibilityError(symbolAccessibilityResult: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
        return {
          diagnosticMessage: Diagnostics.Import_declaration_0_is_using_private_name_1,
          errorNode: node,
          typeName: node.name
        }
      }
    }

    def isVisibleNamedBinding(namedBindings: NamespaceImport | NamedImports): Boolean {
      if (namedBindings) {
        if (namedBindings.kind == SyntaxKind.NamespaceImport) {
          return resolver.isDeclarationVisible(<NamespaceImport>namedBindings)
        }
        else {
          return forEach((<NamedImports>namedBindings).elements, namedImport => resolver.isDeclarationVisible(namedImport))
        }
      }
    }

    def writeImportDeclaration(node: ImportDeclaration) {
      emitJsDocComments(node)
      if (node.flags & NodeFlags.Export) {
        write("export ")
      }
      write("import ")
      if (node.importClause) {
        val currentWriterPos = writer.getTextPos()
        if (node.importClause.name && resolver.isDeclarationVisible(node.importClause)) {
          writeTextOfNode(currentText, node.importClause.name)
        }
        if (node.importClause.namedBindings && isVisibleNamedBinding(node.importClause.namedBindings)) {
          if (currentWriterPos != writer.getTextPos()) {
            // If the default binding was emitted, write the separated
            write(", ")
          }
          if (node.importClause.namedBindings.kind == SyntaxKind.NamespaceImport) {
            write("* as ")
            writeTextOfNode(currentText, (<NamespaceImport>node.importClause.namedBindings).name)
          }
          else {
            write("{ ")
            emitCommaList((<NamedImports>node.importClause.namedBindings).elements, emitImportOrExportSpecifier, resolver.isDeclarationVisible)
            write(" }")
          }
        }
        write(" from ")
      }
      emitExternalModuleSpecifier(node)
      write(";")
      writer.writeLine()
    }

    def emitExternalModuleSpecifier(parent: ImportEqualsDeclaration | ImportDeclaration | ExportDeclaration | ModuleDeclaration) {
      // emitExternalModuleSpecifier is usually called when we emit something in the.d.ts file that will make it an external module (i.e. import/export declarations).
      // the only case when it is not true is when we call it to emit correct name for module augmentation - d.ts files with just module augmentations are not considered
      // external modules since they are indistinguishable from script files with ambient modules. To fix this in such d.ts files we'll emit top level 'export {}'
      // so compiler will treat them as external modules.
      resultHasExternalModuleIndicator = resultHasExternalModuleIndicator || parent.kind != SyntaxKind.ModuleDeclaration
      var moduleSpecifier: Node
      if (parent.kind == SyntaxKind.ImportEqualsDeclaration) {
        val node = parent as ImportEqualsDeclaration
        moduleSpecifier = getExternalModuleImportEqualsDeclarationExpression(node)
      }
      else if (parent.kind == SyntaxKind.ModuleDeclaration) {
        moduleSpecifier = (<ModuleDeclaration>parent).name
      }
      else {
        val node = parent as (ImportDeclaration | ExportDeclaration)
        moduleSpecifier = node.moduleSpecifier
      }

      if (moduleSpecifier.kind == SyntaxKind.StringLiteral && isBundledEmit && (compilerOptions.out || compilerOptions.outFile)) {
        val moduleName = getExternalModuleNameFromDeclaration(host, resolver, parent)
        if (moduleName) {
          write("\"")
          write(moduleName)
          write("\"")
          return
        }
      }

      writeTextOfNode(currentText, moduleSpecifier)
    }

    def emitImportOrExportSpecifier(node: ImportOrExportSpecifier) {
      if (node.propertyName) {
        writeTextOfNode(currentText, node.propertyName)
        write(" as ")
      }
      writeTextOfNode(currentText, node.name)
    }

    def emitExportSpecifier(node: ExportSpecifier) {
      emitImportOrExportSpecifier(node)

      // Make all the declarations visible for the name
      val nodes = resolver.collectLinkedAliases(node.propertyName || node.name)

      // write each of these declarations asynchronously
      writeAsynchronousModuleElements(nodes)
    }

    def emitExportDeclaration(node: ExportDeclaration) {
      emitJsDocComments(node)
      write("export ")
      if (node.exportClause) {
        write("{ ")
        emitCommaList(node.exportClause.elements, emitExportSpecifier)
        write(" }")
      }
      else {
        write("*")
      }
      if (node.moduleSpecifier) {
        write(" from ")
        emitExternalModuleSpecifier(node)
      }
      write(";")
      writer.writeLine()
    }

    def writeModuleDeclaration(node: ModuleDeclaration) {
      emitJsDocComments(node)
      emitModuleElementDeclarationFlags(node)
      if (isGlobalScopeAugmentation(node)) {
        write("global ")
      }
      else {
        if (node.flags & NodeFlags.Namespace) {
          write("package ")
        }
        else {
          write("module ")
        }
        if (isExternalModuleAugmentation(node)) {
          emitExternalModuleSpecifier(node)
        }
        else {
          writeTextOfNode(currentText, node.name)
        }
      }
      while (node.body.kind != SyntaxKind.ModuleBlock) {
        node = <ModuleDeclaration>node.body
        write(".")
        writeTextOfNode(currentText, node.name)
      }
      val prevEnclosingDeclaration = enclosingDeclaration
      enclosingDeclaration = node
      write(" {")
      writeLine()
      increaseIndent()
      emitLines((<ModuleBlock>node.body).statements)
      decreaseIndent()
      write("}")
      writeLine()
      enclosingDeclaration = prevEnclosingDeclaration
    }

    def writeTypeAliasDeclaration(node: TypeAliasDeclaration) {
      val prevEnclosingDeclaration = enclosingDeclaration
      enclosingDeclaration = node
      emitJsDocComments(node)
      emitModuleElementDeclarationFlags(node)
      write("type ")
      writeTextOfNode(currentText, node.name)
      emitTypeParameters(node.typeParameters)
      write(" = ")
      emitTypeWithNewGetSymbolAccessibilityDiagnostic(node.type, getTypeAliasDeclarationVisibilityError)
      write(";")
      writeLine()
      enclosingDeclaration = prevEnclosingDeclaration

      def getTypeAliasDeclarationVisibilityError(symbolAccessibilityResult: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
        return {
          diagnosticMessage: Diagnostics.Exported_type_alias_0_has_or_is_using_private_name_1,
          errorNode: node.type,
          typeName: node.name
        }
      }
    }

    def writeEnumDeclaration(node: EnumDeclaration) {
      emitJsDocComments(node)
      emitModuleElementDeclarationFlags(node)
      if (isConst(node)) {
        write("val ")
      }
      write("enum ")
      writeTextOfNode(currentText, node.name)
      write(" {")
      writeLine()
      increaseIndent()
      emitLines(node.members)
      decreaseIndent()
      write("}")
      writeLine()
    }

    def emitEnumMemberDeclaration(node: EnumMember) {
      emitJsDocComments(node)
      writeTextOfNode(currentText, node.name)
      val enumMemberValue = resolver.getConstantValue(node)
      if (enumMemberValue != undefined) {
        write(" = ")
        write(enumMemberValue.toString())
      }
      write(",")
      writeLine()
    }

    def isPrivateMethodTypeParameter(node: TypeParameterDeclaration) {
      return node.parent.kind == SyntaxKind.MethodDeclaration && (node.parent.flags & NodeFlags.Private)
    }

    def emitTypeParameters(typeParameters: TypeParameterDeclaration[]) {
      def emitTypeParameter(node: TypeParameterDeclaration) {
        increaseIndent()
        emitJsDocComments(node)
        decreaseIndent()
        writeTextOfNode(currentText, node.name)
        // If there is constraint present and this is not a type parameter of the private method emit the constraint
        if (node.constraint && !isPrivateMethodTypeParameter(node)) {
          write(" extends ")
          if (node.parent.kind == SyntaxKind.FunctionType ||
            node.parent.kind == SyntaxKind.ConstructorType ||
            (node.parent.parent && node.parent.parent.kind == SyntaxKind.TypeLiteral)) {
            Debug.assert(node.parent.kind == SyntaxKind.MethodDeclaration ||
              node.parent.kind == SyntaxKind.MethodSignature ||
              node.parent.kind == SyntaxKind.FunctionType ||
              node.parent.kind == SyntaxKind.ConstructorType ||
              node.parent.kind == SyntaxKind.CallSignature ||
              node.parent.kind == SyntaxKind.ConstructSignature)
            emitType(node.constraint)
          }
          else {
            emitTypeWithNewGetSymbolAccessibilityDiagnostic(node.constraint, getTypeParameterConstraintVisibilityError)
          }
        }

        def getTypeParameterConstraintVisibilityError(symbolAccessibilityResult: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
          // Type parameter constraints are named by user so we should always be able to name it
          var diagnosticMessage: DiagnosticMessage
          switch (node.parent.kind) {
            case SyntaxKind.ClassDeclaration:
              diagnosticMessage = Diagnostics.Type_parameter_0_of_exported_class_has_or_is_using_private_name_1
              break

            case SyntaxKind.InterfaceDeclaration:
              diagnosticMessage = Diagnostics.Type_parameter_0_of_exported_interface_has_or_is_using_private_name_1
              break

            case SyntaxKind.ConstructSignature:
              diagnosticMessage = Diagnostics.Type_parameter_0_of_constructor_signature_from_exported_interface_has_or_is_using_private_name_1
              break

            case SyntaxKind.CallSignature:
              diagnosticMessage = Diagnostics.Type_parameter_0_of_call_signature_from_exported_interface_has_or_is_using_private_name_1
              break

            case SyntaxKind.MethodDeclaration:
            case SyntaxKind.MethodSignature:
              if (node.parent.flags & NodeFlags.Static) {
                diagnosticMessage = Diagnostics.Type_parameter_0_of_public_static_method_from_exported_class_has_or_is_using_private_name_1
              }
              else if (node.parent.parent.kind == SyntaxKind.ClassDeclaration) {
                diagnosticMessage = Diagnostics.Type_parameter_0_of_public_method_from_exported_class_has_or_is_using_private_name_1
              }
              else {
                diagnosticMessage = Diagnostics.Type_parameter_0_of_method_from_exported_interface_has_or_is_using_private_name_1
              }
              break

            case SyntaxKind.FunctionDeclaration:
              diagnosticMessage = Diagnostics.Type_parameter_0_of_exported_function_has_or_is_using_private_name_1
              break

            default:
              Debug.fail("This is unknown parent for type parameter: " + node.parent.kind)
          }

          return {
            diagnosticMessage,
            errorNode: node,
            typeName: node.name
          }
        }
      }

      if (typeParameters) {
        write("<")
        emitCommaList(typeParameters, emitTypeParameter)
        write(">")
      }
    }

    def emitHeritageClause(typeReferences: ExpressionWithTypeArguments[], isImplementsList: Boolean) {
      if (typeReferences) {
        write(isImplementsList ? " implements " : " extends ")
        emitCommaList(typeReferences, emitTypeOfTypeReference)
      }

      def emitTypeOfTypeReference(node: ExpressionWithTypeArguments) {
        if (isSupportedExpressionWithTypeArguments(node)) {
          emitTypeWithNewGetSymbolAccessibilityDiagnostic(node, getHeritageClauseVisibilityError)
        }
        else if (!isImplementsList && node.expression.kind == SyntaxKind.NullKeyword) {
          write("null")
        }

        def getHeritageClauseVisibilityError(symbolAccessibilityResult: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
          var diagnosticMessage: DiagnosticMessage
          // Heritage clause is written by user so it can always be named
          if (node.parent.parent.kind == SyntaxKind.ClassDeclaration) {
            // Class or Interface implemented/extended is inaccessible
            diagnosticMessage = isImplementsList ?
              Diagnostics.Implements_clause_of_exported_class_0_has_or_is_using_private_name_1 :
              Diagnostics.Extends_clause_of_exported_class_0_has_or_is_using_private_name_1
          }
          else {
            // interface is inaccessible
            diagnosticMessage = Diagnostics.Extends_clause_of_exported_interface_0_has_or_is_using_private_name_1
          }

          return {
            diagnosticMessage,
            errorNode: node,
            typeName: (<Declaration>node.parent.parent).name
          }
        }
      }
    }

    def writeClassDeclaration(node: ClassDeclaration) {
      def emitParameterProperties(constructorDeclaration: ConstructorDeclaration) {
        if (constructorDeclaration) {
          forEach(constructorDeclaration.parameters, param => {
            if (param.flags & NodeFlags.AccessibilityModifier) {
              emitPropertyDeclaration(param)
            }
          })
        }
      }

      emitJsDocComments(node)
      emitModuleElementDeclarationFlags(node)
      if (node.flags & NodeFlags.Abstract) {
        write("abstract ")
      }

      write("class ")
      writeTextOfNode(currentText, node.name)
      val prevEnclosingDeclaration = enclosingDeclaration
      enclosingDeclaration = node
      emitTypeParameters(node.typeParameters)
      val baseTypeNode = getClassExtendsHeritageClauseElement(node)
      if (baseTypeNode) {
        emitHeritageClause([baseTypeNode], /*isImplementsList*/ false)
      }
      emitHeritageClause(getClassImplementsHeritageClauseElements(node), /*isImplementsList*/ true)
      write(" {")
      writeLine()
      increaseIndent()
      emitParameterProperties(getFirstConstructorWithBody(node))
      emitLines(node.members)
      decreaseIndent()
      write("}")
      writeLine()
      enclosingDeclaration = prevEnclosingDeclaration
    }

    def writeInterfaceDeclaration(node: InterfaceDeclaration) {
      emitJsDocComments(node)
      emitModuleElementDeclarationFlags(node)
      write("interface ")
      writeTextOfNode(currentText, node.name)
      val prevEnclosingDeclaration = enclosingDeclaration
      enclosingDeclaration = node
      emitTypeParameters(node.typeParameters)
      emitHeritageClause(getInterfaceBaseTypeNodes(node), /*isImplementsList*/ false)
      write(" {")
      writeLine()
      increaseIndent()
      emitLines(node.members)
      decreaseIndent()
      write("}")
      writeLine()
      enclosingDeclaration = prevEnclosingDeclaration
    }

    def emitPropertyDeclaration(node: Declaration) {
      if (hasDynamicName(node)) {
        return
      }

      emitJsDocComments(node)
      emitClassMemberDeclarationFlags(node.flags)
      emitVariableDeclaration(<VariableDeclaration>node)
      write(";")
      writeLine()
    }

    def emitVariableDeclaration(node: VariableDeclaration) {
      // If we are emitting property it isn't moduleElement and hence we already know it needs to be emitted
      // so there is no check needed to see if declaration is visible
      if (node.kind != SyntaxKind.VariableDeclaration || resolver.isDeclarationVisible(node)) {
        if (isBindingPattern(node.name)) {
          emitBindingPattern(<BindingPattern>node.name)
        }
        else {
          // If this node is a computed name, it can only be a symbol, because we've already skipped
          // it if it's not a well known symbol. In that case, the text of the name will be exactly
          // what we want, namely the name expression enclosed in brackets.
          writeTextOfNode(currentText, node.name)
          // If optional property emit ?
          if ((node.kind == SyntaxKind.PropertyDeclaration || node.kind == SyntaxKind.PropertySignature) && hasQuestionToken(node)) {
            write("?")
          }
          if ((node.kind == SyntaxKind.PropertyDeclaration || node.kind == SyntaxKind.PropertySignature) && node.parent.kind == SyntaxKind.TypeLiteral) {
            emitTypeOfVariableDeclarationFromTypeLiteral(node)
          }
          else if (!(node.flags & NodeFlags.Private)) {
            writeTypeOfDeclaration(node, node.type, getVariableDeclarationTypeVisibilityError)
          }
        }
      }

      def getVariableDeclarationTypeVisibilityDiagnosticMessage(symbolAccessibilityResult: SymbolAccessibilityResult) {
        if (node.kind == SyntaxKind.VariableDeclaration) {
          return symbolAccessibilityResult.errorModuleName ?
            symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
              Diagnostics.Exported_variable_0_has_or_is_using_name_1_from_external_module_2_but_cannot_be_named :
              Diagnostics.Exported_variable_0_has_or_is_using_name_1_from_private_module_2 :
            Diagnostics.Exported_variable_0_has_or_is_using_private_name_1
        }
        // This check is to ensure we don't report error on constructor parameter property as that error would be reported during parameter emit
        else if (node.kind == SyntaxKind.PropertyDeclaration || node.kind == SyntaxKind.PropertySignature) {
          // TODO(jfreeman): Deal with computed properties in error reporting.
          if (node.flags & NodeFlags.Static) {
            return symbolAccessibilityResult.errorModuleName ?
              symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                Diagnostics.Public_static_property_0_of_exported_class_has_or_is_using_name_1_from_external_module_2_but_cannot_be_named :
                Diagnostics.Public_static_property_0_of_exported_class_has_or_is_using_name_1_from_private_module_2 :
              Diagnostics.Public_static_property_0_of_exported_class_has_or_is_using_private_name_1
          }
          else if (node.parent.kind == SyntaxKind.ClassDeclaration) {
            return symbolAccessibilityResult.errorModuleName ?
              symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                Diagnostics.Public_property_0_of_exported_class_has_or_is_using_name_1_from_external_module_2_but_cannot_be_named :
                Diagnostics.Public_property_0_of_exported_class_has_or_is_using_name_1_from_private_module_2 :
              Diagnostics.Public_property_0_of_exported_class_has_or_is_using_private_name_1
          }
          else {
            // Interfaces cannot have types that cannot be named
            return symbolAccessibilityResult.errorModuleName ?
              Diagnostics.Property_0_of_exported_interface_has_or_is_using_name_1_from_private_module_2 :
              Diagnostics.Property_0_of_exported_interface_has_or_is_using_private_name_1
          }
        }
      }

      def getVariableDeclarationTypeVisibilityError(symbolAccessibilityResult: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
        val diagnosticMessage = getVariableDeclarationTypeVisibilityDiagnosticMessage(symbolAccessibilityResult)
        return diagnosticMessage != undefined ? {
          diagnosticMessage,
          errorNode: node,
          typeName: node.name
        } : undefined
      }

      def emitBindingPattern(bindingPattern: BindingPattern) {
        // Only select non-omitted expression from the bindingPattern's elements.
        // We have to do this to avoid emitting trailing commas.
        // For example:
        //    original: var [, c,,] = [ 2,3,4]
        //    emitted: declare var c: Int; // instead of declare var c:Int,
        val elements: Node[] = []
        for (val element of bindingPattern.elements) {
          if (element.kind != SyntaxKind.OmittedExpression) {
            elements.push(element)
          }
        }
        emitCommaList(elements, emitBindingElement)
      }

      def emitBindingElement(bindingElement: BindingElement) {
        def getBindingElementTypeVisibilityError(symbolAccessibilityResult: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
          val diagnosticMessage = getVariableDeclarationTypeVisibilityDiagnosticMessage(symbolAccessibilityResult)
          return diagnosticMessage != undefined ? {
            diagnosticMessage,
            errorNode: bindingElement,
            typeName: bindingElement.name
          } : undefined
        }

        if (bindingElement.name) {
          if (isBindingPattern(bindingElement.name)) {
            emitBindingPattern(<BindingPattern>bindingElement.name)
          }
          else {
            writeTextOfNode(currentText, bindingElement.name)
            writeTypeOfDeclaration(bindingElement, /*type*/ undefined, getBindingElementTypeVisibilityError)
          }
        }
      }
    }

    def emitTypeOfVariableDeclarationFromTypeLiteral(node: VariableLikeDeclaration) {
      // if this is property of type literal,
      // or is parameter of method/call/construct/index signature of type literal
      // emit only if type is specified
      if (node.type) {
        write(": ")
        emitType(node.type)
      }
    }

    def isVariableStatementVisible(node: VariableStatement) {
      return forEach(node.declarationList.declarations, varDeclaration => resolver.isDeclarationVisible(varDeclaration))
    }

    def writeVariableStatement(node: VariableStatement) {
      emitJsDocComments(node)
      emitModuleElementDeclarationFlags(node)
      if (isLet(node.declarationList)) {
        write("var ")
      }
      else if (isConst(node.declarationList)) {
        write("val ")
      }
      else {
        write("var ")
      }
      emitCommaList(node.declarationList.declarations, emitVariableDeclaration, resolver.isDeclarationVisible)
      write(";")
      writeLine()
    }

    def emitAccessorDeclaration(node: AccessorDeclaration) {
      if (hasDynamicName(node)) {
        return
      }

      val accessors = getAllAccessorDeclarations((<ClassDeclaration>node.parent).members, node)
      var accessorWithTypeAnnotation: AccessorDeclaration

      if (node == accessors.firstAccessor) {
        emitJsDocComments(accessors.getAccessor)
        emitJsDocComments(accessors.setAccessor)
        emitClassMemberDeclarationFlags(node.flags | (accessors.setAccessor ? 0 : NodeFlags.Readonly))
        writeTextOfNode(currentText, node.name)
        if (!(node.flags & NodeFlags.Private)) {
          accessorWithTypeAnnotation = node
          var type = getTypeAnnotationFromAccessor(node)
          if (!type) {
            // couldn't get type for the first accessor, try the another one
            val anotherAccessor = node.kind == SyntaxKind.GetAccessor ? accessors.setAccessor : accessors.getAccessor
            type = getTypeAnnotationFromAccessor(anotherAccessor)
            if (type) {
              accessorWithTypeAnnotation = anotherAccessor
            }
          }
          writeTypeOfDeclaration(node, type, getAccessorDeclarationTypeVisibilityError)
        }
        write(";")
        writeLine()
      }

      def getTypeAnnotationFromAccessor(accessor: AccessorDeclaration): TypeNode {
        if (accessor) {
          return accessor.kind == SyntaxKind.GetAccessor
            ? accessor.type // Getter - return type
            : accessor.parameters.length > 0
              ? accessor.parameters[0].type // Setter parameter type
              : undefined
        }
      }

      def getAccessorDeclarationTypeVisibilityError(symbolAccessibilityResult: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
        var diagnosticMessage: DiagnosticMessage
        if (accessorWithTypeAnnotation.kind == SyntaxKind.SetAccessor) {
          // Setters have to have type named and cannot infer it so, the type should always be named
          if (accessorWithTypeAnnotation.parent.flags & NodeFlags.Static) {
            diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
              Diagnostics.Parameter_0_of_public_static_property_setter_from_exported_class_has_or_is_using_name_1_from_private_module_2 :
              Diagnostics.Parameter_0_of_public_static_property_setter_from_exported_class_has_or_is_using_private_name_1
          }
          else {
            diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
              Diagnostics.Parameter_0_of_public_property_setter_from_exported_class_has_or_is_using_name_1_from_private_module_2 :
              Diagnostics.Parameter_0_of_public_property_setter_from_exported_class_has_or_is_using_private_name_1
          }
          return {
            diagnosticMessage,
            errorNode: <Node>accessorWithTypeAnnotation.parameters[0],
            // TODO(jfreeman): Investigate why we are passing node.name instead of node.parameters[0].name
            typeName: accessorWithTypeAnnotation.name
          }
        }
        else {
          if (accessorWithTypeAnnotation.flags & NodeFlags.Static) {
            diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
              symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                Diagnostics.Return_type_of_public_static_property_getter_from_exported_class_has_or_is_using_name_0_from_external_module_1_but_cannot_be_named :
                Diagnostics.Return_type_of_public_static_property_getter_from_exported_class_has_or_is_using_name_0_from_private_module_1 :
              Diagnostics.Return_type_of_public_static_property_getter_from_exported_class_has_or_is_using_private_name_0
          }
          else {
            diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
              symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                Diagnostics.Return_type_of_public_property_getter_from_exported_class_has_or_is_using_name_0_from_external_module_1_but_cannot_be_named :
                Diagnostics.Return_type_of_public_property_getter_from_exported_class_has_or_is_using_name_0_from_private_module_1 :
              Diagnostics.Return_type_of_public_property_getter_from_exported_class_has_or_is_using_private_name_0
          }
          return {
            diagnosticMessage,
            errorNode: <Node>accessorWithTypeAnnotation.name,
            typeName: undefined
          }
        }
      }
    }

    def writeFunctionDeclaration(node: FunctionLikeDeclaration) {
      if (hasDynamicName(node)) {
        return
      }

      // If we are emitting Method/Constructor it isn't moduleElement and hence already determined to be emitting
      // so no need to verify if the declaration is visible
      if (!resolver.isImplementationOfOverload(node)) {
        emitJsDocComments(node)
        if (node.kind == SyntaxKind.FunctionDeclaration) {
          emitModuleElementDeclarationFlags(node)
        }
        else if (node.kind == SyntaxKind.MethodDeclaration) {
          emitClassMemberDeclarationFlags(node.flags)
        }
        if (node.kind == SyntaxKind.FunctionDeclaration) {
          write("def ")
          writeTextOfNode(currentText, node.name)
        }
        else if (node.kind == SyntaxKind.Constructor) {
          write("constructor")
        }
        else {
          writeTextOfNode(currentText, node.name)
          if (hasQuestionToken(node)) {
            write("?")
          }
        }
        emitSignatureDeclaration(node)
      }
    }

    def emitSignatureDeclarationWithJsDocComments(node: SignatureDeclaration) {
      emitJsDocComments(node)
      emitSignatureDeclaration(node)
    }

    def emitSignatureDeclaration(node: SignatureDeclaration) {
      val prevEnclosingDeclaration = enclosingDeclaration
      enclosingDeclaration = node

      if (node.kind == SyntaxKind.IndexSignature) {
        // Index signature can have readonly modifier
        emitClassMemberDeclarationFlags(node.flags)
        write("[")
      }
      else {
        // Construct signature or constructor type write new Signature
        if (node.kind == SyntaxKind.ConstructSignature || node.kind == SyntaxKind.ConstructorType) {
          write("new ")
        }
        emitTypeParameters(node.typeParameters)
        write("(")
      }

      // Parameters
      emitCommaList(node.parameters, emitParameterDeclaration)

      if (node.kind == SyntaxKind.IndexSignature) {
        write("]")
      }
      else {
        write(")")
      }

      // If this is not a constructor and is not private, emit the return type
      val isFunctionTypeOrConstructorType = node.kind == SyntaxKind.FunctionType || node.kind == SyntaxKind.ConstructorType
      if (isFunctionTypeOrConstructorType || node.parent.kind == SyntaxKind.TypeLiteral) {
        // Emit type literal signature return type only if specified
        if (node.type) {
          write(isFunctionTypeOrConstructorType ? " => " : ": ")
          emitType(node.type)
        }
      }
      else if (node.kind != SyntaxKind.Constructor && !(node.flags & NodeFlags.Private)) {
        writeReturnTypeAtSignature(node, getReturnTypeVisibilityError)
      }

      enclosingDeclaration = prevEnclosingDeclaration

      if (!isFunctionTypeOrConstructorType) {
        write(";")
        writeLine()
      }

      def getReturnTypeVisibilityError(symbolAccessibilityResult: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
        var diagnosticMessage: DiagnosticMessage
        switch (node.kind) {
          case SyntaxKind.ConstructSignature:
            // Interfaces cannot have return types that cannot be named
            diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
              Diagnostics.Return_type_of_constructor_signature_from_exported_interface_has_or_is_using_name_0_from_private_module_1 :
              Diagnostics.Return_type_of_constructor_signature_from_exported_interface_has_or_is_using_private_name_0
            break

          case SyntaxKind.CallSignature:
            // Interfaces cannot have return types that cannot be named
            diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
              Diagnostics.Return_type_of_call_signature_from_exported_interface_has_or_is_using_name_0_from_private_module_1 :
              Diagnostics.Return_type_of_call_signature_from_exported_interface_has_or_is_using_private_name_0
            break

          case SyntaxKind.IndexSignature:
            // Interfaces cannot have return types that cannot be named
            diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
              Diagnostics.Return_type_of_index_signature_from_exported_interface_has_or_is_using_name_0_from_private_module_1 :
              Diagnostics.Return_type_of_index_signature_from_exported_interface_has_or_is_using_private_name_0
            break

          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.MethodSignature:
            if (node.flags & NodeFlags.Static) {
              diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
                symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                  Diagnostics.Return_type_of_public_static_method_from_exported_class_has_or_is_using_name_0_from_external_module_1_but_cannot_be_named :
                  Diagnostics.Return_type_of_public_static_method_from_exported_class_has_or_is_using_name_0_from_private_module_1 :
                Diagnostics.Return_type_of_public_static_method_from_exported_class_has_or_is_using_private_name_0
            }
            else if (node.parent.kind == SyntaxKind.ClassDeclaration) {
              diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
                symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                  Diagnostics.Return_type_of_public_method_from_exported_class_has_or_is_using_name_0_from_external_module_1_but_cannot_be_named :
                  Diagnostics.Return_type_of_public_method_from_exported_class_has_or_is_using_name_0_from_private_module_1 :
                Diagnostics.Return_type_of_public_method_from_exported_class_has_or_is_using_private_name_0
            }
            else {
              // Interfaces cannot have return types that cannot be named
              diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
                Diagnostics.Return_type_of_method_from_exported_interface_has_or_is_using_name_0_from_private_module_1 :
                Diagnostics.Return_type_of_method_from_exported_interface_has_or_is_using_private_name_0
            }
            break

          case SyntaxKind.FunctionDeclaration:
            diagnosticMessage = symbolAccessibilityResult.errorModuleName ?
              symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                Diagnostics.Return_type_of_exported_function_has_or_is_using_name_0_from_external_module_1_but_cannot_be_named :
                Diagnostics.Return_type_of_exported_function_has_or_is_using_name_0_from_private_module_1 :
              Diagnostics.Return_type_of_exported_function_has_or_is_using_private_name_0
            break

          default:
            Debug.fail("This is unknown kind for signature: " + node.kind)
        }

        return {
          diagnosticMessage,
          errorNode: <Node>node.name || node
        }
      }
    }

    def emitParameterDeclaration(node: ParameterDeclaration) {
      increaseIndent()
      emitJsDocComments(node)
      if (node.dotDotDotToken) {
        write("...")
      }
      if (isBindingPattern(node.name)) {
        // For bindingPattern, we can't simply writeTextOfNode from the source file
        // because we want to omit the initializer and using writeTextOfNode will result in initializer get emitted.
        // Therefore, we will have to recursively emit each element in the bindingPattern.
        emitBindingPattern(<BindingPattern>node.name)
      }
      else {
        writeTextOfNode(currentText, node.name)
      }
      if (resolver.isOptionalParameter(node)) {
        write("?")
      }
      decreaseIndent()

      if (node.parent.kind == SyntaxKind.FunctionType ||
        node.parent.kind == SyntaxKind.ConstructorType ||
        node.parent.parent.kind == SyntaxKind.TypeLiteral) {
        emitTypeOfVariableDeclarationFromTypeLiteral(node)
      }
      else if (!(node.parent.flags & NodeFlags.Private)) {
        writeTypeOfDeclaration(node, node.type, getParameterDeclarationTypeVisibilityError)
      }

      def getParameterDeclarationTypeVisibilityError(symbolAccessibilityResult: SymbolAccessibilityResult): SymbolAccessibilityDiagnostic {
        val diagnosticMessage: DiagnosticMessage = getParameterDeclarationTypeVisibilityDiagnosticMessage(symbolAccessibilityResult)
        return diagnosticMessage != undefined ? {
          diagnosticMessage,
          errorNode: node,
          typeName: node.name
        } : undefined
      }

      def getParameterDeclarationTypeVisibilityDiagnosticMessage(symbolAccessibilityResult: SymbolAccessibilityResult): DiagnosticMessage {
        switch (node.parent.kind) {
          case SyntaxKind.Constructor:
            return symbolAccessibilityResult.errorModuleName ?
              symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                Diagnostics.Parameter_0_of_constructor_from_exported_class_has_or_is_using_name_1_from_external_module_2_but_cannot_be_named :
                Diagnostics.Parameter_0_of_constructor_from_exported_class_has_or_is_using_name_1_from_private_module_2 :
              Diagnostics.Parameter_0_of_constructor_from_exported_class_has_or_is_using_private_name_1

          case SyntaxKind.ConstructSignature:
            // Interfaces cannot have parameter types that cannot be named
            return symbolAccessibilityResult.errorModuleName ?
              Diagnostics.Parameter_0_of_constructor_signature_from_exported_interface_has_or_is_using_name_1_from_private_module_2 :
              Diagnostics.Parameter_0_of_constructor_signature_from_exported_interface_has_or_is_using_private_name_1

          case SyntaxKind.CallSignature:
            // Interfaces cannot have parameter types that cannot be named
            return symbolAccessibilityResult.errorModuleName ?
              Diagnostics.Parameter_0_of_call_signature_from_exported_interface_has_or_is_using_name_1_from_private_module_2 :
              Diagnostics.Parameter_0_of_call_signature_from_exported_interface_has_or_is_using_private_name_1

          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.MethodSignature:
            if (node.parent.flags & NodeFlags.Static) {
              return symbolAccessibilityResult.errorModuleName ?
                symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                  Diagnostics.Parameter_0_of_public_static_method_from_exported_class_has_or_is_using_name_1_from_external_module_2_but_cannot_be_named :
                  Diagnostics.Parameter_0_of_public_static_method_from_exported_class_has_or_is_using_name_1_from_private_module_2 :
                Diagnostics.Parameter_0_of_public_static_method_from_exported_class_has_or_is_using_private_name_1
            }
            else if (node.parent.parent.kind == SyntaxKind.ClassDeclaration) {
               return symbolAccessibilityResult.errorModuleName ?
                symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                  Diagnostics.Parameter_0_of_public_method_from_exported_class_has_or_is_using_name_1_from_external_module_2_but_cannot_be_named :
                  Diagnostics.Parameter_0_of_public_method_from_exported_class_has_or_is_using_name_1_from_private_module_2 :
                Diagnostics.Parameter_0_of_public_method_from_exported_class_has_or_is_using_private_name_1
            }
            else {
              // Interfaces cannot have parameter types that cannot be named
              return symbolAccessibilityResult.errorModuleName ?
                Diagnostics.Parameter_0_of_method_from_exported_interface_has_or_is_using_name_1_from_private_module_2 :
                Diagnostics.Parameter_0_of_method_from_exported_interface_has_or_is_using_private_name_1
            }

          case SyntaxKind.FunctionDeclaration:
            return symbolAccessibilityResult.errorModuleName ?
              symbolAccessibilityResult.accessibility == SymbolAccessibility.CannotBeNamed ?
                Diagnostics.Parameter_0_of_exported_function_has_or_is_using_name_1_from_external_module_2_but_cannot_be_named :
                Diagnostics.Parameter_0_of_exported_function_has_or_is_using_name_1_from_private_module_2 :
              Diagnostics.Parameter_0_of_exported_function_has_or_is_using_private_name_1

          default:
            Debug.fail("This is unknown parent for parameter: " + node.parent.kind)
        }
      }

      def emitBindingPattern(bindingPattern: BindingPattern) {
        // We have to explicitly emit square bracket and bracket because these tokens are not store inside the node.
        if (bindingPattern.kind == SyntaxKind.ObjectBindingPattern) {
          write("{")
          emitCommaList(bindingPattern.elements, emitBindingElement)
          write("}")
        }
        else if (bindingPattern.kind == SyntaxKind.ArrayBindingPattern) {
          write("[")
          val elements = bindingPattern.elements
          emitCommaList(elements, emitBindingElement)
          if (elements && elements.hasTrailingComma) {
            write(", ")
          }
          write("]")
        }
      }

      def emitBindingElement(bindingElement: BindingElement) {

        if (bindingElement.kind == SyntaxKind.OmittedExpression) {
          // If bindingElement is an omittedExpression (i.e. containing elision),
          // we will emit blank space (although this may differ from users' original code,
          // it allows emitSeparatedList to write separator appropriately)
          // Example:
          //    original: def foo([, x, ,]) {}
          //    emit  : def foo([ , x,  , ]) {}
          write(" ")
        }
        else if (bindingElement.kind == SyntaxKind.BindingElement) {
          if (bindingElement.propertyName) {
            // bindingElement has propertyName property in the following case:
            //    { y: [a,b,c] ...} -> bindingPattern will have a property called propertyName for "y"
            // We have to explicitly emit the propertyName before descending into its binding elements.
            // Example:
            //    original: def foo({y: [a,b,c]}) {}
            //    emit  : declare def foo({y: [a, b, c]}: { y: [any, any, any] }) void
            writeTextOfNode(currentText, bindingElement.propertyName)
            write(": ")
          }
          if (bindingElement.name) {
            if (isBindingPattern(bindingElement.name)) {
              // If it is a nested binding pattern, we will recursively descend into each element and emit each one separately.
              // In the case of rest element, we will omit rest element.
              // Example:
              //    original: def foo([a, [[b]], c] = [1,[["String"]], 3]) {}
              //    emit  : declare def foo([a, [[b]], c]: [Int, [[String]], Int]): void
              //    original with rest: def foo([a, ...c]) {}
              //    emit        : declare def foo([a, ...c]): void
              emitBindingPattern(<BindingPattern>bindingElement.name)
            }
            else {
              Debug.assert(bindingElement.name.kind == SyntaxKind.Identifier)
              // If the node is just an identifier, we will simply emit the text associated with the node's name
              // Example:
              //    original: def foo({y = 10, x}) {}
              //    emit  : declare def foo({y, x}: {Int, any}): void
              if (bindingElement.dotDotDotToken) {
                write("...")
              }
              writeTextOfNode(currentText, bindingElement.name)
            }
          }
        }
      }
    }

    def emitNode(node: Node) {
      switch (node.kind) {
        case SyntaxKind.FunctionDeclaration:
        case SyntaxKind.ModuleDeclaration:
        case SyntaxKind.ImportEqualsDeclaration:
        case SyntaxKind.InterfaceDeclaration:
        case SyntaxKind.ClassDeclaration:
        case SyntaxKind.TypeAliasDeclaration:
        case SyntaxKind.EnumDeclaration:
          return emitModuleElement(node, isModuleElementVisible(<Declaration>node))
        case SyntaxKind.VariableStatement:
          return emitModuleElement(node, isVariableStatementVisible(<VariableStatement>node))
        case SyntaxKind.ImportDeclaration:
          // Import declaration without import clause is visible, otherwise it is not visible
          return emitModuleElement(node, /*isModuleElementVisible*/!(<ImportDeclaration>node).importClause)
        case SyntaxKind.ExportDeclaration:
          return emitExportDeclaration(<ExportDeclaration>node)
        case SyntaxKind.Constructor:
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.MethodSignature:
          return writeFunctionDeclaration(<FunctionLikeDeclaration>node)
        case SyntaxKind.ConstructSignature:
        case SyntaxKind.CallSignature:
        case SyntaxKind.IndexSignature:
          return emitSignatureDeclarationWithJsDocComments(<SignatureDeclaration>node)
        case SyntaxKind.GetAccessor:
        case SyntaxKind.SetAccessor:
          return emitAccessorDeclaration(<AccessorDeclaration>node)
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
          return emitPropertyDeclaration(<PropertyDeclaration>node)
        case SyntaxKind.EnumMember:
          return emitEnumMemberDeclaration(<EnumMember>node)
        case SyntaxKind.ExportAssignment:
          return emitExportAssignment(<ExportAssignment>node)
        case SyntaxKind.SourceFile:
          return emitSourceFile(<SourceFile>node)
      }
    }

    /**
     * Adds the reference to referenced file, returns true if global file reference was emitted
     * @param referencedFile
     * @param addBundledFileReference Determines if global file reference corresponding to bundled file should be emitted or not
     */
    def writeReferencePath(referencedFile: SourceFile, addBundledFileReference: Boolean): Boolean {
      var declFileName: String
      var addedBundledEmitReference = false
      if (isDeclarationFile(referencedFile)) {
        // Declaration file, use declaration file name
        declFileName = referencedFile.fileName
      }
      else {
        // Get the declaration file path
        forEachExpectedEmitFile(host, getDeclFileName, referencedFile)
      }

      if (declFileName) {
        declFileName = getRelativePathToDirectoryOrUrl(
          getDirectoryPath(normalizeSlashes(declarationFilePath)),
          declFileName,
          host.getCurrentDirectory(),
          host.getCanonicalFileName,
          /*isAbsolutePathAnUrl*/ false)

        referencePathsOutput += "/// <reference path=\"" + declFileName + "\" />" + newLine
      }
      return addedBundledEmitReference

      def getDeclFileName(emitFileNames: EmitFileNames, sourceFiles: SourceFile[], isBundledEmit: Boolean) {
        // Dont add reference path to this file if it is a bundled emit and caller asked not emit bundled file path
        if (isBundledEmit && !addBundledFileReference) {
          return
        }

        Debug.assert(!!emitFileNames.declarationFilePath || isSourceFileJavaScript(referencedFile), "Declaration file is not present only for javascript files")
        declFileName = emitFileNames.declarationFilePath || emitFileNames.jsFilePath
        addedBundledEmitReference = isBundledEmit
      }
    }
  }

  /* @internal */
  def writeDeclarationFile(declarationFilePath: String, sourceFiles: SourceFile[], isBundledEmit: Boolean, host: EmitHost, resolver: EmitResolver, emitterDiagnostics: DiagnosticCollection) {
    val emitDeclarationResult = emitDeclarations(host, resolver, emitterDiagnostics, declarationFilePath, sourceFiles, isBundledEmit)
    val emitSkipped = emitDeclarationResult.reportedDeclarationError || host.isEmitBlocked(declarationFilePath) || host.getCompilerOptions().noEmit
    if (!emitSkipped) {
      val declarationOutput = emitDeclarationResult.referencePathsOutput
        + getDeclarationOutput(emitDeclarationResult.synchronousDeclarationOutput, emitDeclarationResult.moduleElementDeclarationEmitInfo)
      writeFile(host, emitterDiagnostics, declarationFilePath, declarationOutput, host.getCompilerOptions().emitBOM)
    }
    return emitSkipped

    def getDeclarationOutput(synchronousDeclarationOutput: String, moduleElementDeclarationEmitInfo: ModuleElementDeclarationEmitInfo[]) {
      var appliedSyncOutputPos = 0
      var declarationOutput = ""
      // apply asynchronous additions to the synchronous output
      forEach(moduleElementDeclarationEmitInfo, aliasEmitInfo => {
        if (aliasEmitInfo.asynchronousOutput) {
          declarationOutput += synchronousDeclarationOutput.substring(appliedSyncOutputPos, aliasEmitInfo.outputPos)
          declarationOutput += getDeclarationOutput(aliasEmitInfo.asynchronousOutput, aliasEmitInfo.subModuleElementDeclarationEmitInfo)
          appliedSyncOutputPos = aliasEmitInfo.outputPos
        }
      })
      declarationOutput += synchronousDeclarationOutput.substring(appliedSyncOutputPos)
      return declarationOutput
    }
  }
}
