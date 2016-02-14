package be.doeraene.tsc

/// <reference path="sys.ts" />

/* @internal */
object Utilities {
  trait ReferencePathMatchResult {
    fileReference?: FileReference
    diagnosticMessage?: DiagnosticMessage
    isNoDefaultLib?: Boolean
  }

  trait SynthesizedNode extends Node {
    leadingCommentRanges?: CommentRange[]
    trailingCommentRanges?: CommentRange[]
    startsOnNewLine: Boolean
  }

  def getDeclarationOfKind(symbol: Symbol, kind: SyntaxKind): Declaration {
    val declarations = symbol.declarations
    if (declarations) {
      for (val declaration of declarations) {
        if (declaration.kind == kind) {
          return declaration
        }
      }
    }

    return ()
  }

  trait StringSymbolWriter extends SymbolWriter {
    String(): String
  }

  trait EmitHost extends ScriptReferenceHost {
    getSourceFiles(): SourceFile[]

    getCommonSourceDirectory(): String
    getCanonicalFileName(fileName: String): String
    getNewLine(): String

    isEmitBlocked(emitFileName: String): Boolean

    writeFile: WriteFileCallback
  }

  // Pool writers to avoid needing to allocate them for every symbol we write.
  val stringWriters: StringSymbolWriter[] = []
  def getSingleLineStringWriter(): StringSymbolWriter {
    if (stringWriters.length == 0) {
      var str = ""

      val writeText: (text: String) => Unit = text => str += text
      return {
        String: () => str,
        writeKeyword: writeText,
        writeOperator: writeText,
        writePunctuation: writeText,
        writeSpace: writeText,
        writeStringLiteral: writeText,
        writeParameter: writeText,
        writeSymbol: writeText,

        // Completely ignore indentation for String writers.  And map newlines to
        // a single space.
        writeLine: () => str += " ",
        increaseIndent: () => { },
        decreaseIndent: () => { },
        clear: () => str = "",
        trackSymbol: () => { },
        reportInaccessibleThisError: () => { }
      }
    }

    return stringWriters.pop()
  }

  def releaseStringWriter(writer: StringSymbolWriter) {
    writer.clear()
    stringWriters.push(writer)
  }

  def getFullWidth(node: Node) {
    return node.end - node.pos
  }

  def arrayIsEqualTo<T>(array1: T[], array2: T[], equaler?: (a: T, b: T) => Boolean): Boolean {
    if (!array1 || !array2) {
      return array1 == array2
    }

    if (array1.length != array2.length) {
      return false
    }

    for (var i = 0; i < array1.length; i++) {
      val equals = equaler ? equaler(array1[i], array2[i]) : array1[i] == array2[i]
      if (!equals) {
        return false
      }
    }

    return true
  }

  def hasResolvedModule(sourceFile: SourceFile, moduleNameText: String): Boolean {
    return sourceFile.resolvedModules && hasProperty(sourceFile.resolvedModules, moduleNameText)
  }

  def getResolvedModule(sourceFile: SourceFile, moduleNameText: String): ResolvedModule {
    return hasResolvedModule(sourceFile, moduleNameText) ? sourceFile.resolvedModules[moduleNameText] : ()
  }

  def setResolvedModule(sourceFile: SourceFile, moduleNameText: String, resolvedModule: ResolvedModule): Unit {
    if (!sourceFile.resolvedModules) {
      sourceFile.resolvedModules = {}
    }

    sourceFile.resolvedModules[moduleNameText] = resolvedModule
  }

  // Returns true if this node contains a parse error anywhere underneath it.
  def containsParseError(node: Node): Boolean {
    aggregateChildData(node)
    return (node.flags & NodeFlags.ThisNodeOrAnySubNodesHasError) != 0
  }

  def aggregateChildData(node: Node): Unit {
    if (!(node.flags & NodeFlags.HasAggregatedChildData)) {
      // A node is considered to contain a parse error if:
      //  a) the parser explicitly marked that it had an error
      //  b) any of it's children reported that it had an error.
      val thisNodeOrAnySubNodesHasError = ((node.flags & NodeFlags.ThisNodeHasError) != 0) ||
        forEachChild(node, containsParseError)

      // If so, mark ourselves accordingly.
      if (thisNodeOrAnySubNodesHasError) {
        node.flags |= NodeFlags.ThisNodeOrAnySubNodesHasError
      }

      // Also mark that we've propagated the child information to this node.  This way we can
      // always consult the bit directly on this node without needing to check its children
      // again.
      node.flags |= NodeFlags.HasAggregatedChildData
    }
  }

  def getSourceFileOfNode(node: Node): SourceFile {
    while (node && node.kind != SyntaxKind.SourceFile) {
      node = node.parent
    }
    return <SourceFile>node
  }

  def isStatementWithLocals(node: Node) {
    switch (node.kind) {
      case SyntaxKind.Block:
      case SyntaxKind.CaseBlock:
      case SyntaxKind.ForStatement:
      case SyntaxKind.ForInStatement:
      case SyntaxKind.ForOfStatement:
        return true
    }
    return false
  }

  def getStartPositionOfLine(line: Int, sourceFile: SourceFile): Int {
    Debug.assert(line >= 0)
    return getLineStarts(sourceFile)[line]
  }

  // This is a useful def for debugging purposes.
  def nodePosToString(node: Node): String {
    val file = getSourceFileOfNode(node)
    val loc = getLineAndCharacterOfPosition(file, node.pos)
    return `${ file.fileName }(${ loc.line + 1 },${ loc.character + 1 })`
  }

  def getStartPosOfNode(node: Node): Int {
    return node.pos
  }

  // Returns true if this node is missing from the actual source code. A 'missing' node is different
  // from '()/defined'. When a node is () (which can happen for optional nodes
  // in the tree), it is definitely missing. However, a node may be defined, but still be
  // missing.  This happens whenever the parser knows it needs to parse something, but can't
  // get anything in the source code that it expects at that location. For example:
  //
  //      var a:
  //
  // Here, the Type in the Type-Annotation is not-optional (as there is a colon in the source
  // code). So the parser will attempt to parse out a type, and will create an actual node.
  // However, this node will be 'missing' in the sense that no actual source-code/tokens are
  // contained within it.
  def nodeIsMissing(node: Node) {
    if (!node) {
      return true
    }

    return node.pos == node.end && node.pos >= 0 && node.kind != SyntaxKind.EndOfFileToken
  }

  def nodeIsPresent(node: Node) {
    return !nodeIsMissing(node)
  }

  def getTokenPosOfNode(node: Node, sourceFile?: SourceFile): Int {
    // With nodes that have no width (i.e. 'Missing' nodes), we actually *don't*
    // want to skip trivia because this will launch us forward to the next token.
    if (nodeIsMissing(node)) {
      return node.pos
    }

    return skipTrivia((sourceFile || getSourceFileOfNode(node)).text, node.pos)
  }

  def getNonDecoratorTokenPosOfNode(node: Node, sourceFile?: SourceFile): Int {
    if (nodeIsMissing(node) || !node.decorators) {
      return getTokenPosOfNode(node, sourceFile)
    }

    return skipTrivia((sourceFile || getSourceFileOfNode(node)).text, node.decorators.end)
  }

  def getSourceTextOfNodeFromSourceFile(sourceFile: SourceFile, node: Node, includeTrivia = false): String {
    if (nodeIsMissing(node)) {
      return ""
    }

    val text = sourceFile.text
    return text.substring(includeTrivia ? node.pos : skipTrivia(text, node.pos), node.end)
  }

  def getTextOfNodeFromSourceText(sourceText: String, node: Node): String {
    if (nodeIsMissing(node)) {
      return ""
    }

    return sourceText.substring(skipTrivia(sourceText, node.pos), node.end)
  }

  def getTextOfNode(node: Node, includeTrivia = false): String {
    return getSourceTextOfNodeFromSourceFile(getSourceFileOfNode(node), node, includeTrivia)
  }

  // Add an extra underscore to identifiers that start with two underscores to avoid issues with magic names like '__proto__'
  def escapeIdentifier(identifier: String): String {
    return identifier.length >= 2 && identifier.charCodeAt(0) == CharacterCodes._ && identifier.charCodeAt(1) == CharacterCodes._ ? "_" + identifier : identifier
  }

  // Remove extra underscore from escaped identifier
  def unescapeIdentifier(identifier: String): String {
    return identifier.length >= 3 && identifier.charCodeAt(0) == CharacterCodes._ && identifier.charCodeAt(1) == CharacterCodes._ && identifier.charCodeAt(2) == CharacterCodes._ ? identifier.substr(1) : identifier
  }

  // Make an identifier from an external module name by extracting the String after the last "/" and replacing
  // all non-alphanumeric characters with underscores
  def makeIdentifierFromModuleName(moduleName: String): String {
    return getBaseFileName(moduleName).replace(/^(\d)/, "_$1").replace(/\W/g, "_")
  }

  def isBlockOrCatchScoped(declaration: Declaration) {
    return (getCombinedNodeFlags(declaration) & NodeFlags.BlockScoped) != 0 ||
      isCatchClauseVariableDeclaration(declaration)
  }

  def isAmbientModule(node: Node): Boolean {
    return node && node.kind == SyntaxKind.ModuleDeclaration &&
      ((<ModuleDeclaration>node).name.kind == SyntaxKind.StringLiteral || isGlobalScopeAugmentation(<ModuleDeclaration>node))
  }

  def isBlockScopedContainerTopLevel(node: Node): Boolean {
    return node.kind == SyntaxKind.SourceFile ||
      node.kind == SyntaxKind.ModuleDeclaration ||
      isFunctionLike(node) ||
      isFunctionBlock(node)
  }

  def isGlobalScopeAugmentation(module: ModuleDeclaration): Boolean {
    return !!(module.flags & NodeFlags.GlobalAugmentation)
  }

  def isExternalModuleAugmentation(node: Node): Boolean {
    // external module augmentation is a ambient module declaration that is either:
    // - defined in the top level scope and source file is an external module
    // - defined inside ambient module declaration located in the top level scope and source file not an external module
    if (!node || !isAmbientModule(node)) {
      return false
    }
    switch (node.parent.kind) {
      case SyntaxKind.SourceFile:
        return isExternalModule(<SourceFile>node.parent)
      case SyntaxKind.ModuleBlock:
        return isAmbientModule(node.parent.parent) && !isExternalModule(<SourceFile>node.parent.parent.parent)
    }
    return false
  }

  // Gets the nearest enclosing block scope container that has the provided node
  // as a descendant, that is not the provided node.
  def getEnclosingBlockScopeContainer(node: Node): Node {
    var current = node.parent
    while (current) {
      if (isFunctionLike(current)) {
        return current
      }
      switch (current.kind) {
        case SyntaxKind.SourceFile:
        case SyntaxKind.CaseBlock:
        case SyntaxKind.CatchClause:
        case SyntaxKind.ModuleDeclaration:
        case SyntaxKind.ForStatement:
        case SyntaxKind.ForInStatement:
        case SyntaxKind.ForOfStatement:
          return current
        case SyntaxKind.Block:
          // def block is not considered block-scope container
          // see comment in binder.ts: bind(...), case for SyntaxKind.Block
          if (!isFunctionLike(current.parent)) {
            return current
          }
      }

      current = current.parent
    }
  }

  def isCatchClauseVariableDeclaration(declaration: Declaration) {
    return declaration &&
      declaration.kind == SyntaxKind.VariableDeclaration &&
      declaration.parent &&
      declaration.parent.kind == SyntaxKind.CatchClause
  }

  // Return display name of an identifier
  // Computed property names will just be emitted as "[<expr>]", where <expr> is the source
  // text of the expression in the computed property.
  def declarationNameToString(name: DeclarationName) {
    return getFullWidth(name) == 0 ? "(Missing)" : getTextOfNode(name)
  }

  def createDiagnosticForNode(node: Node, message: DiagnosticMessage, arg0?: any, arg1?: any, arg2?: any): Diagnostic {
    val sourceFile = getSourceFileOfNode(node)
    val span = getErrorSpanForNode(sourceFile, node)
    return createFileDiagnostic(sourceFile, span.start, span.length, message, arg0, arg1, arg2)
  }

  def createDiagnosticForNodeFromMessageChain(node: Node, messageChain: DiagnosticMessageChain): Diagnostic {
    val sourceFile = getSourceFileOfNode(node)
    val span = getErrorSpanForNode(sourceFile, node)
    return {
      file: sourceFile,
      start: span.start,
      length: span.length,
      code: messageChain.code,
      category: messageChain.category,
      messageText: messageChain.next ? messageChain : messageChain.messageText
    }
  }

  def getSpanOfTokenAtPosition(sourceFile: SourceFile, pos: Int): TextSpan {
    val scanner = createScanner(sourceFile.languageVersion, /*skipTrivia*/ true, sourceFile.languageVariant, sourceFile.text, /*onError:*/ (), pos)
    scanner.scan()
    val start = scanner.getTokenPos()
    return createTextSpanFromBounds(start, scanner.getTextPos())
  }

  def getErrorSpanForNode(sourceFile: SourceFile, node: Node): TextSpan {
    var errorNode = node
    switch (node.kind) {
      case SyntaxKind.SourceFile:
        var pos = skipTrivia(sourceFile.text, 0, /*stopAfterLineBreak*/ false)
        if (pos == sourceFile.text.length) {
          // file is empty - return span for the beginning of the file
          return createTextSpan(0, 0)
        }
        return getSpanOfTokenAtPosition(sourceFile, pos)
      // This list is a work in progress. Add missing node kinds to improve their error
      // spans.
      case SyntaxKind.VariableDeclaration:
      case SyntaxKind.BindingElement:
      case SyntaxKind.ClassDeclaration:
      case SyntaxKind.ClassExpression:
      case SyntaxKind.InterfaceDeclaration:
      case SyntaxKind.ModuleDeclaration:
      case SyntaxKind.EnumDeclaration:
      case SyntaxKind.EnumMember:
      case SyntaxKind.FunctionDeclaration:
      case SyntaxKind.FunctionExpression:
      case SyntaxKind.MethodDeclaration:
      case SyntaxKind.GetAccessor:
      case SyntaxKind.SetAccessor:
      case SyntaxKind.TypeAliasDeclaration:
        errorNode = (<Declaration>node).name
        break
    }

    if (errorNode == ()) {
      // If we don't have a better node, then just set the error on the first token of
      // construct.
      return getSpanOfTokenAtPosition(sourceFile, node.pos)
    }

    val pos = nodeIsMissing(errorNode)
      ? errorNode.pos
      : skipTrivia(sourceFile.text, errorNode.pos)

    return createTextSpanFromBounds(pos, errorNode.end)
  }

  def isExternalModule(file: SourceFile): Boolean {
    return file.externalModuleIndicator != ()
  }

  def isExternalOrCommonJsModule(file: SourceFile): Boolean {
    return (file.externalModuleIndicator || file.commonJsModuleIndicator) != ()
  }

  def isDeclarationFile(file: SourceFile): Boolean {
    return file.isDeclarationFile
  }

  def isConstEnumDeclaration(node: Node): Boolean {
    return node.kind == SyntaxKind.EnumDeclaration && isConst(node)
  }

  def walkUpBindingElementsAndPatterns(node: Node): Node {
    while (node && (node.kind == SyntaxKind.BindingElement || isBindingPattern(node))) {
      node = node.parent
    }

    return node
  }

  // Returns the node flags for this node and all relevant parent nodes.  This is done so that
  // nodes like variable declarations and binding elements can returned a view of their flags
  // that includes the modifiers from their container.  i.e. flags like export/declare aren't
  // stored on the variable declaration directly, but on the containing variable statement
  // (if it has one).  Similarly, flags for var/val are store on the variable declaration
  // list.  By calling this def, all those flags are combined so that the client can treat
  // the node as if it actually had those flags.
  def getCombinedNodeFlags(node: Node): NodeFlags {
    node = walkUpBindingElementsAndPatterns(node)

    var flags = node.flags
    if (node.kind == SyntaxKind.VariableDeclaration) {
      node = node.parent
    }

    if (node && node.kind == SyntaxKind.VariableDeclarationList) {
      flags |= node.flags
      node = node.parent
    }

    if (node && node.kind == SyntaxKind.VariableStatement) {
      flags |= node.flags
    }

    return flags
  }

  def isConst(node: Node): Boolean {
    return !!(getCombinedNodeFlags(node) & NodeFlags.Const)
  }

  def isLet(node: Node): Boolean {
    return !!(getCombinedNodeFlags(node) & NodeFlags.Let)
  }

  def isSuperCallExpression(n: Node): Boolean {
    return n.kind == SyntaxKind.CallExpression && (<CallExpression>n).expression.kind == SyntaxKind.SuperKeyword
  }

  def isPrologueDirective(node: Node): Boolean {
    return node.kind == SyntaxKind.ExpressionStatement && (<ExpressionStatement>node).expression.kind == SyntaxKind.StringLiteral
  }

  def getLeadingCommentRangesOfNode(node: Node, sourceFileOfNode: SourceFile) {
    return getLeadingCommentRanges(sourceFileOfNode.text, node.pos)
  }

  def getLeadingCommentRangesOfNodeFromText(node: Node, text: String) {
    return getLeadingCommentRanges(text, node.pos)
  }

  def getJsDocComments(node: Node, sourceFileOfNode: SourceFile) {
    return getJsDocCommentsFromText(node, sourceFileOfNode.text)
  }

  def getJsDocCommentsFromText(node: Node, text: String) {
    val commentRanges = (node.kind == SyntaxKind.Parameter || node.kind == SyntaxKind.TypeParameter) ?
      concatenate(getTrailingCommentRanges(text, node.pos),
        getLeadingCommentRanges(text, node.pos)) :
      getLeadingCommentRangesOfNodeFromText(node, text)
    return filter(commentRanges, isJsDocComment)

    def isJsDocComment(comment: CommentRange) {
      // True if the comment starts with '/**' but not if it is '/**/'
      return text.charCodeAt(comment.pos + 1) == CharacterCodes.asterisk &&
        text.charCodeAt(comment.pos + 2) == CharacterCodes.asterisk &&
        text.charCodeAt(comment.pos + 3) != CharacterCodes.slash
    }
  }

  var fullTripleSlashReferencePathRegEx = /^(\/\/\/\s*<reference\s+path\s*=\s*)('|")(.+?)\2.*?\/>/
  var fullTripleSlashAMDReferencePathRegEx = /^(\/\/\/\s*<amd-dependency\s+path\s*=\s*)('|")(.+?)\2.*?\/>/

  def isTypeNode(node: Node): Boolean {
    if (SyntaxKind.FirstTypeNode <= node.kind && node.kind <= SyntaxKind.LastTypeNode) {
      return true
    }

    switch (node.kind) {
      case SyntaxKind.AnyKeyword:
      case SyntaxKind.NumberKeyword:
      case SyntaxKind.StringKeyword:
      case SyntaxKind.BooleanKeyword:
      case SyntaxKind.SymbolKeyword:
        return true
      case SyntaxKind.VoidKeyword:
        return node.parent.kind != SyntaxKind.VoidExpression
      case SyntaxKind.ExpressionWithTypeArguments:
        return !isExpressionWithTypeArgumentsInClassExtendsClause(node)

      // Identifiers and qualified names may be type nodes, depending on their context. Climb
      // above them to find the lowest container
      case SyntaxKind.Identifier:
        // If the identifier is the RHS of a qualified name, then it's a type iff its parent is.
        if (node.parent.kind == SyntaxKind.QualifiedName && (<QualifiedName>node.parent).right == node) {
          node = node.parent
        }
        else if (node.parent.kind == SyntaxKind.PropertyAccessExpression && (<PropertyAccessExpression>node.parent).name == node) {
          node = node.parent
        }
        // At this point, node is either a qualified name or an identifier
        Debug.assert(node.kind == SyntaxKind.Identifier || node.kind == SyntaxKind.QualifiedName || node.kind == SyntaxKind.PropertyAccessExpression,
          "'node' was expected to be a qualified name, identifier or property access in 'isTypeNode'.")
      case SyntaxKind.QualifiedName:
      case SyntaxKind.PropertyAccessExpression:
      case SyntaxKind.ThisKeyword:
        var parent = node.parent
        if (parent.kind == SyntaxKind.TypeQuery) {
          return false
        }
        // Do not recursively call isTypeNode on the parent. In the example:
        //
        //   var a: A.B.C
        //
        // Calling isTypeNode would consider the qualified name A.B a type node. Only C or
        // A.B.C is a type node.
        if (SyntaxKind.FirstTypeNode <= parent.kind && parent.kind <= SyntaxKind.LastTypeNode) {
          return true
        }
        switch (parent.kind) {
          case SyntaxKind.ExpressionWithTypeArguments:
            return !isExpressionWithTypeArgumentsInClassExtendsClause(parent)
          case SyntaxKind.TypeParameter:
            return node == (<TypeParameterDeclaration>parent).constraint
          case SyntaxKind.PropertyDeclaration:
          case SyntaxKind.PropertySignature:
          case SyntaxKind.Parameter:
          case SyntaxKind.VariableDeclaration:
            return node == (<VariableLikeDeclaration>parent).type
          case SyntaxKind.FunctionDeclaration:
          case SyntaxKind.FunctionExpression:
          case SyntaxKind.ArrowFunction:
          case SyntaxKind.Constructor:
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.MethodSignature:
          case SyntaxKind.GetAccessor:
          case SyntaxKind.SetAccessor:
            return node == (<FunctionLikeDeclaration>parent).type
          case SyntaxKind.CallSignature:
          case SyntaxKind.ConstructSignature:
          case SyntaxKind.IndexSignature:
            return node == (<SignatureDeclaration>parent).type
          case SyntaxKind.TypeAssertionExpression:
            return node == (<TypeAssertion>parent).type
          case SyntaxKind.CallExpression:
          case SyntaxKind.NewExpression:
            return (<CallExpression>parent).typeArguments && indexOf((<CallExpression>parent).typeArguments, node) >= 0
          case SyntaxKind.TaggedTemplateExpression:
            // TODO (drosen): TaggedTemplateExpressions may eventually support type arguments.
            return false
        }
    }

    return false
  }

  // Warning: This has the same semantics as the forEach family of functions,
  //      in that traversal terminates in the event that 'visitor' supplies a truthy value.
  def forEachReturnStatement<T>(body: Block, visitor: (stmt: ReturnStatement) => T): T {

    return traverse(body)

    def traverse(node: Node): T {
      switch (node.kind) {
        case SyntaxKind.ReturnStatement:
          return visitor(<ReturnStatement>node)
        case SyntaxKind.CaseBlock:
        case SyntaxKind.Block:
        case SyntaxKind.IfStatement:
        case SyntaxKind.DoStatement:
        case SyntaxKind.WhileStatement:
        case SyntaxKind.ForStatement:
        case SyntaxKind.ForInStatement:
        case SyntaxKind.ForOfStatement:
        case SyntaxKind.WithStatement:
        case SyntaxKind.SwitchStatement:
        case SyntaxKind.CaseClause:
        case SyntaxKind.DefaultClause:
        case SyntaxKind.LabeledStatement:
        case SyntaxKind.TryStatement:
        case SyntaxKind.CatchClause:
          return forEachChild(node, traverse)
      }
    }
  }

  def forEachYieldExpression(body: Block, visitor: (expr: YieldExpression) => Unit): Unit {

    return traverse(body)

    def traverse(node: Node): Unit {
      switch (node.kind) {
        case SyntaxKind.YieldExpression:
          visitor(<YieldExpression>node)
          var operand = (<YieldExpression>node).expression
          if (operand) {
            traverse(operand)
          }
        case SyntaxKind.EnumDeclaration:
        case SyntaxKind.InterfaceDeclaration:
        case SyntaxKind.ModuleDeclaration:
        case SyntaxKind.TypeAliasDeclaration:
        case SyntaxKind.ClassDeclaration:
        case SyntaxKind.ClassExpression:
          // These are not allowed inside a generator now, but eventually they may be allowed
          // as local types. Regardless, any yield statements contained within them should be
          // skipped in this traversal.
          return
        default:
          if (isFunctionLike(node)) {
            val name = (<FunctionLikeDeclaration>node).name
            if (name && name.kind == SyntaxKind.ComputedPropertyName) {
              // Note that we will not include methods/accessors of a class because they would require
              // first descending into the class. This is by design.
              traverse((<ComputedPropertyName>name).expression)
              return
            }
          }
          else if (!isTypeNode(node)) {
            // This is the general case, which should include mostly expressions and statements.
            // Also includes NodeArrays.
            forEachChild(node, traverse)
          }
      }
    }
  }

  def isVariableLike(node: Node): node is VariableLikeDeclaration {
    if (node) {
      switch (node.kind) {
        case SyntaxKind.BindingElement:
        case SyntaxKind.EnumMember:
        case SyntaxKind.Parameter:
        case SyntaxKind.PropertyAssignment:
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
        case SyntaxKind.ShorthandPropertyAssignment:
        case SyntaxKind.VariableDeclaration:
          return true
      }
    }
    return false
  }

  def isAccessor(node: Node): node is AccessorDeclaration {
    return node && (node.kind == SyntaxKind.GetAccessor || node.kind == SyntaxKind.SetAccessor)
  }

  def isClassLike(node: Node): node is ClassLikeDeclaration {
    return node && (node.kind == SyntaxKind.ClassDeclaration || node.kind == SyntaxKind.ClassExpression)
  }

  def isFunctionLike(node: Node): node is FunctionLikeDeclaration {
    return node && isFunctionLikeKind(node.kind)
  }

  def isFunctionLikeKind(kind: SyntaxKind): Boolean {
    switch (kind) {
      case SyntaxKind.Constructor:
      case SyntaxKind.FunctionExpression:
      case SyntaxKind.FunctionDeclaration:
      case SyntaxKind.ArrowFunction:
      case SyntaxKind.MethodDeclaration:
      case SyntaxKind.MethodSignature:
      case SyntaxKind.GetAccessor:
      case SyntaxKind.SetAccessor:
      case SyntaxKind.CallSignature:
      case SyntaxKind.ConstructSignature:
      case SyntaxKind.IndexSignature:
      case SyntaxKind.FunctionType:
      case SyntaxKind.ConstructorType:
        return true
    }
  }

  def introducesArgumentsExoticObject(node: Node) {
    switch (node.kind) {
      case SyntaxKind.MethodDeclaration:
      case SyntaxKind.MethodSignature:
      case SyntaxKind.Constructor:
      case SyntaxKind.GetAccessor:
      case SyntaxKind.SetAccessor:
      case SyntaxKind.FunctionDeclaration:
      case SyntaxKind.FunctionExpression:
        return true
    }
    return false
  }

  def isIterationStatement(node: Node, lookInLabeledStatements: Boolean): Boolean {
    switch (node.kind) {
      case SyntaxKind.ForStatement:
      case SyntaxKind.ForInStatement:
      case SyntaxKind.ForOfStatement:
      case SyntaxKind.DoStatement:
      case SyntaxKind.WhileStatement:
        return true
      case SyntaxKind.LabeledStatement:
        return lookInLabeledStatements && isIterationStatement((<LabeledStatement>node).statement, lookInLabeledStatements)
    }

    return false
  }


  def isFunctionBlock(node: Node) {
    return node && node.kind == SyntaxKind.Block && isFunctionLike(node.parent)
  }

  def isObjectLiteralMethod(node: Node): node is MethodDeclaration {
    return node && node.kind == SyntaxKind.MethodDeclaration && node.parent.kind == SyntaxKind.ObjectLiteralExpression
  }

  def isIdentifierTypePredicate(predicate: TypePredicate): predicate is IdentifierTypePredicate {
    return predicate && predicate.kind == TypePredicateKind.Identifier
  }

  def getContainingFunction(node: Node): FunctionLikeDeclaration {
    while (true) {
      node = node.parent
      if (!node || isFunctionLike(node)) {
        return <FunctionLikeDeclaration>node
      }
    }
  }

  def getContainingClass(node: Node): ClassLikeDeclaration {
    while (true) {
      node = node.parent
      if (!node || isClassLike(node)) {
        return <ClassLikeDeclaration>node
      }
    }
  }

  def getThisContainer(node: Node, includeArrowFunctions: Boolean): Node {
    while (true) {
      node = node.parent
      if (!node) {
        return ()
      }
      switch (node.kind) {
        case SyntaxKind.ComputedPropertyName:
          // If the grandparent node is an object literal (as opposed to a class),
          // then the computed property is not a 'this' container.
          // A computed property name in a class needs to be a this container
          // so that we can error on it.
          if (isClassLike(node.parent.parent)) {
            return node
          }
          // If this is a computed property, then the parent should not
          // make it a this container. The parent might be a property
          // in an object literal, like a method or accessor. But in order for
          // such a parent to be a this container, the reference must be in
          // the *body* of the container.
          node = node.parent
          break
        case SyntaxKind.Decorator:
          // Decorators are always applied outside of the body of a class or method.
          if (node.parent.kind == SyntaxKind.Parameter && isClassElement(node.parent.parent)) {
            // If the decorator's parent is a Parameter, we resolve the this container from
            // the grandparent class declaration.
            node = node.parent.parent
          }
          else if (isClassElement(node.parent)) {
            // If the decorator's parent is a class element, we resolve the 'this' container
            // from the parent class declaration.
            node = node.parent
          }
          break
        case SyntaxKind.ArrowFunction:
          if (!includeArrowFunctions) {
            continue
          }
        // Fall through
        case SyntaxKind.FunctionDeclaration:
        case SyntaxKind.FunctionExpression:
        case SyntaxKind.ModuleDeclaration:
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.MethodSignature:
        case SyntaxKind.Constructor:
        case SyntaxKind.GetAccessor:
        case SyntaxKind.SetAccessor:
        case SyntaxKind.CallSignature:
        case SyntaxKind.ConstructSignature:
        case SyntaxKind.IndexSignature:
        case SyntaxKind.EnumDeclaration:
        case SyntaxKind.SourceFile:
          return node
      }
    }
  }

  /**
    * Given an super call\property node returns a closest node where either
    * - super call\property is legal in the node and not legal in the parent node the node.
    *   i.e. super call is legal in constructor but not legal in the class body.
    * - node is arrow def (so caller might need to call getSuperContainer in case it needs to climb higher)
    * - super call\property is definitely illegal in the node (but might be legal in some subnode)
    *   i.e. super property access is illegal in def declaration but can be legal in the statement list
    */
  def getSuperContainer(node: Node, stopOnFunctions: Boolean): Node {
    while (true) {
      node = node.parent
      if (!node) {
        return node
      }
      switch (node.kind) {
        case SyntaxKind.ComputedPropertyName:
          node = node.parent
          break
        case SyntaxKind.FunctionDeclaration:
        case SyntaxKind.FunctionExpression:
        case SyntaxKind.ArrowFunction:
          if (!stopOnFunctions) {
            continue
          }
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.MethodSignature:
        case SyntaxKind.Constructor:
        case SyntaxKind.GetAccessor:
        case SyntaxKind.SetAccessor:
          return node
        case SyntaxKind.Decorator:
          // Decorators are always applied outside of the body of a class or method.
          if (node.parent.kind == SyntaxKind.Parameter && isClassElement(node.parent.parent)) {
            // If the decorator's parent is a Parameter, we resolve the this container from
            // the grandparent class declaration.
            node = node.parent.parent
          }
          else if (isClassElement(node.parent)) {
            // If the decorator's parent is a class element, we resolve the 'this' container
            // from the parent class declaration.
            node = node.parent
          }
          break
      }
    }
  }

  /**
   * Determines whether a node is a property or element access expression for super.
   */
  def isSuperPropertyOrElementAccess(node: Node) {
    return (node.kind == SyntaxKind.PropertyAccessExpression
      || node.kind == SyntaxKind.ElementAccessExpression)
      && (<PropertyAccessExpression | ElementAccessExpression>node).expression.kind == SyntaxKind.SuperKeyword
  }


  def getEntityNameFromTypeNode(node: TypeNode): EntityName | Expression {
    if (node) {
      switch (node.kind) {
        case SyntaxKind.TypeReference:
          return (<TypeReferenceNode>node).typeName
        case SyntaxKind.ExpressionWithTypeArguments:
          return (<ExpressionWithTypeArguments>node).expression
        case SyntaxKind.Identifier:
        case SyntaxKind.QualifiedName:
          return (<EntityName><Node>node)
      }
    }

    return ()
  }

  def getInvokedExpression(node: CallLikeExpression): Expression {
    if (node.kind == SyntaxKind.TaggedTemplateExpression) {
      return (<TaggedTemplateExpression>node).tag
    }

    // Will either be a CallExpression, NewExpression, or Decorator.
    return (<CallExpression | Decorator>node).expression
  }

  def nodeCanBeDecorated(node: Node): Boolean {
    switch (node.kind) {
      case SyntaxKind.ClassDeclaration:
        // classes are valid targets
        return true

      case SyntaxKind.PropertyDeclaration:
        // property declarations are valid if their parent is a class declaration.
        return node.parent.kind == SyntaxKind.ClassDeclaration

      case SyntaxKind.GetAccessor:
      case SyntaxKind.SetAccessor:
      case SyntaxKind.MethodDeclaration:
        // if this method has a body and its parent is a class declaration, this is a valid target.
        return (<FunctionLikeDeclaration>node).body != ()
          && node.parent.kind == SyntaxKind.ClassDeclaration

      case SyntaxKind.Parameter:
        // if the parameter's parent has a body and its grandparent is a class declaration, this is a valid target
        return (<FunctionLikeDeclaration>node.parent).body != ()
          && (node.parent.kind == SyntaxKind.Constructor
          || node.parent.kind == SyntaxKind.MethodDeclaration
          || node.parent.kind == SyntaxKind.SetAccessor)
          && node.parent.parent.kind == SyntaxKind.ClassDeclaration
    }

    return false
  }

  def nodeIsDecorated(node: Node): Boolean {
    return node.decorators != ()
      && nodeCanBeDecorated(node)
  }

  def isPropertyAccessExpression(node: Node): node is PropertyAccessExpression {
    return node.kind == SyntaxKind.PropertyAccessExpression
  }

  def isElementAccessExpression(node: Node): node is ElementAccessExpression {
    return node.kind == SyntaxKind.ElementAccessExpression
  }

  def isExpression(node: Node): Boolean {
    switch (node.kind) {
      case SyntaxKind.SuperKeyword:
      case SyntaxKind.NullKeyword:
      case SyntaxKind.TrueKeyword:
      case SyntaxKind.FalseKeyword:
      case SyntaxKind.RegularExpressionLiteral:
      case SyntaxKind.ArrayLiteralExpression:
      case SyntaxKind.ObjectLiteralExpression:
      case SyntaxKind.PropertyAccessExpression:
      case SyntaxKind.ElementAccessExpression:
      case SyntaxKind.CallExpression:
      case SyntaxKind.NewExpression:
      case SyntaxKind.TaggedTemplateExpression:
      case SyntaxKind.AsExpression:
      case SyntaxKind.TypeAssertionExpression:
      case SyntaxKind.ParenthesizedExpression:
      case SyntaxKind.FunctionExpression:
      case SyntaxKind.ClassExpression:
      case SyntaxKind.ArrowFunction:
      case SyntaxKind.VoidExpression:
      case SyntaxKind.DeleteExpression:
      case SyntaxKind.TypeOfExpression:
      case SyntaxKind.PrefixUnaryExpression:
      case SyntaxKind.PostfixUnaryExpression:
      case SyntaxKind.BinaryExpression:
      case SyntaxKind.ConditionalExpression:
      case SyntaxKind.SpreadElementExpression:
      case SyntaxKind.TemplateExpression:
      case SyntaxKind.NoSubstitutionTemplateLiteral:
      case SyntaxKind.OmittedExpression:
      case SyntaxKind.JsxElement:
      case SyntaxKind.JsxSelfClosingElement:
      case SyntaxKind.YieldExpression:
      case SyntaxKind.AwaitExpression:
        return true
      case SyntaxKind.QualifiedName:
        while (node.parent.kind == SyntaxKind.QualifiedName) {
          node = node.parent
        }
        return node.parent.kind == SyntaxKind.TypeQuery
      case SyntaxKind.Identifier:
        if (node.parent.kind == SyntaxKind.TypeQuery) {
          return true
        }
      // fall through
      case SyntaxKind.NumericLiteral:
      case SyntaxKind.StringLiteral:
      case SyntaxKind.ThisKeyword:
        var parent = node.parent
        switch (parent.kind) {
          case SyntaxKind.VariableDeclaration:
          case SyntaxKind.Parameter:
          case SyntaxKind.PropertyDeclaration:
          case SyntaxKind.PropertySignature:
          case SyntaxKind.EnumMember:
          case SyntaxKind.PropertyAssignment:
          case SyntaxKind.BindingElement:
            return (<VariableLikeDeclaration>parent).initializer == node
          case SyntaxKind.ExpressionStatement:
          case SyntaxKind.IfStatement:
          case SyntaxKind.DoStatement:
          case SyntaxKind.WhileStatement:
          case SyntaxKind.ReturnStatement:
          case SyntaxKind.WithStatement:
          case SyntaxKind.SwitchStatement:
          case SyntaxKind.CaseClause:
          case SyntaxKind.ThrowStatement:
          case SyntaxKind.SwitchStatement:
            return (<ExpressionStatement>parent).expression == node
          case SyntaxKind.ForStatement:
            var forStatement = <ForStatement>parent
            return (forStatement.initializer == node && forStatement.initializer.kind != SyntaxKind.VariableDeclarationList) ||
              forStatement.condition == node ||
              forStatement.incrementor == node
          case SyntaxKind.ForInStatement:
          case SyntaxKind.ForOfStatement:
            var forInStatement = <ForInStatement | ForOfStatement>parent
            return (forInStatement.initializer == node && forInStatement.initializer.kind != SyntaxKind.VariableDeclarationList) ||
              forInStatement.expression == node
          case SyntaxKind.TypeAssertionExpression:
          case SyntaxKind.AsExpression:
            return node == (<AssertionExpression>parent).expression
          case SyntaxKind.TemplateSpan:
            return node == (<TemplateSpan>parent).expression
          case SyntaxKind.ComputedPropertyName:
            return node == (<ComputedPropertyName>parent).expression
          case SyntaxKind.Decorator:
          case SyntaxKind.JsxExpression:
          case SyntaxKind.JsxSpreadAttribute:
            return true
          case SyntaxKind.ExpressionWithTypeArguments:
            return (<ExpressionWithTypeArguments>parent).expression == node && isExpressionWithTypeArgumentsInClassExtendsClause(parent)
          default:
            if (isExpression(parent)) {
              return true
            }
        }
    }
    return false
  }

  def isExternalModuleNameRelative(moduleName: String): Boolean {
    // TypeScript 1.0 spec (April 2014): 11.2.1
    // An external module name is "relative" if the first term is "." or "..".
    return moduleName.substr(0, 2) == "./" || moduleName.substr(0, 3) == "../" || moduleName.substr(0, 2) == ".\\" || moduleName.substr(0, 3) == "..\\"
  }

  def isInstantiatedModule(node: ModuleDeclaration, preserveConstEnums: Boolean) {
    val moduleState = getModuleInstanceState(node)
    return moduleState == ModuleInstanceState.Instantiated ||
      (preserveConstEnums && moduleState == ModuleInstanceState.ConstEnumOnly)
  }

  def isExternalModuleImportEqualsDeclaration(node: Node) {
    return node.kind == SyntaxKind.ImportEqualsDeclaration && (<ImportEqualsDeclaration>node).moduleReference.kind == SyntaxKind.ExternalModuleReference
  }

  def getExternalModuleImportEqualsDeclarationExpression(node: Node) {
    Debug.assert(isExternalModuleImportEqualsDeclaration(node))
    return (<ExternalModuleReference>(<ImportEqualsDeclaration>node).moduleReference).expression
  }

  def isInternalModuleImportEqualsDeclaration(node: Node): node is ImportEqualsDeclaration {
    return node.kind == SyntaxKind.ImportEqualsDeclaration && (<ImportEqualsDeclaration>node).moduleReference.kind != SyntaxKind.ExternalModuleReference
  }

  def isSourceFileJavaScript(file: SourceFile): Boolean {
    return isInJavaScriptFile(file)
  }

  def isInJavaScriptFile(node: Node): Boolean {
    return node && !!(node.flags & NodeFlags.JavaScriptFile)
  }

  /**
   * Returns true if the node is a CallExpression to the identifier 'require' with
   * exactly one argument.
   * This def does not test if the node is in a JavaScript file or not.
  */
  def isRequireCall(expression: Node, checkArgumentIsStringLiteral: Boolean): expression is CallExpression {
    // of the form 'require("name")'
    val isRequire = expression.kind == SyntaxKind.CallExpression &&
      (<CallExpression>expression).expression.kind == SyntaxKind.Identifier &&
      (<Identifier>(<CallExpression>expression).expression).text == "require" &&
      (<CallExpression>expression).arguments.length == 1

    return isRequire && (!checkArgumentIsStringLiteral || (<CallExpression>expression).arguments[0].kind == SyntaxKind.StringLiteral)
  }

  /// Given a BinaryExpression, returns SpecialPropertyAssignmentKind for the various kinds of property
  /// assignments we treat as special in the binder
  def getSpecialPropertyAssignmentKind(expression: Node): SpecialPropertyAssignmentKind {
    if (expression.kind != SyntaxKind.BinaryExpression) {
      return SpecialPropertyAssignmentKind.None
    }
    val expr = <BinaryExpression>expression
    if (expr.operatorToken.kind != SyntaxKind.EqualsToken || expr.left.kind != SyntaxKind.PropertyAccessExpression) {
      return SpecialPropertyAssignmentKind.None
    }
    val lhs = <PropertyAccessExpression>expr.left
    if (lhs.expression.kind == SyntaxKind.Identifier) {
      val lhsId = <Identifier>lhs.expression
      if (lhsId.text == "exports") {
        // exports.name = expr
        return SpecialPropertyAssignmentKind.ExportsProperty
      }
      else if (lhsId.text == "module" && lhs.name.text == "exports") {
        // module.exports = expr
        return SpecialPropertyAssignmentKind.ModuleExports
      }
    }
    else if (lhs.expression.kind == SyntaxKind.ThisKeyword) {
      return SpecialPropertyAssignmentKind.ThisProperty
    }
    else if (lhs.expression.kind == SyntaxKind.PropertyAccessExpression) {
      // chained dot, e.g. x.y.z = expr; this var is the 'x.y' part
      val innerPropertyAccess = <PropertyAccessExpression>lhs.expression
      if (innerPropertyAccess.expression.kind == SyntaxKind.Identifier && innerPropertyAccess.name.text == "prototype") {
        return SpecialPropertyAssignmentKind.PrototypeProperty
      }
    }

    return SpecialPropertyAssignmentKind.None
  }

  def getExternalModuleName(node: Node): Expression {
    if (node.kind == SyntaxKind.ImportDeclaration) {
      return (<ImportDeclaration>node).moduleSpecifier
    }
    if (node.kind == SyntaxKind.ImportEqualsDeclaration) {
      val reference = (<ImportEqualsDeclaration>node).moduleReference
      if (reference.kind == SyntaxKind.ExternalModuleReference) {
        return (<ExternalModuleReference>reference).expression
      }
    }
    if (node.kind == SyntaxKind.ExportDeclaration) {
      return (<ExportDeclaration>node).moduleSpecifier
    }
    if (node.kind == SyntaxKind.ModuleDeclaration && (<ModuleDeclaration>node).name.kind == SyntaxKind.StringLiteral) {
      return (<ModuleDeclaration>node).name
    }
  }

  def hasQuestionToken(node: Node) {
    if (node) {
      switch (node.kind) {
        case SyntaxKind.Parameter:
        case SyntaxKind.MethodDeclaration:
        case SyntaxKind.MethodSignature:
        case SyntaxKind.ShorthandPropertyAssignment:
        case SyntaxKind.PropertyAssignment:
        case SyntaxKind.PropertyDeclaration:
        case SyntaxKind.PropertySignature:
          return (<ParameterDeclaration | MethodDeclaration | PropertyDeclaration>node).questionToken != ()
      }
    }

    return false
  }

  def isJSDocConstructSignature(node: Node) {
    return node.kind == SyntaxKind.JSDocFunctionType &&
      (<JSDocFunctionType>node).parameters.length > 0 &&
      (<JSDocFunctionType>node).parameters[0].type.kind == SyntaxKind.JSDocConstructorType
  }

  def getJSDocTag(node: Node, kind: SyntaxKind, checkParentVariableStatement: Boolean): JSDocTag {
    if (!node) {
      return ()
    }

    val jsDocComment = getJSDocComment(node, checkParentVariableStatement)
    if (!jsDocComment) {
      return ()
    }

    for (val tag of jsDocComment.tags) {
      if (tag.kind == kind) {
        return tag
      }
    }
  }

  def getJSDocComment(node: Node, checkParentVariableStatement: Boolean): JSDocComment {
    if (node.jsDocComment) {
      return node.jsDocComment
    }
    // Try to recognize this pattern when node is initializer of variable declaration and JSDoc comments are on containing variable statement.
    // /**
    //   * @param {Int} name
    //   * @returns {Int}
    //   */
    // var x = def(name) { return name.length; }
    if (checkParentVariableStatement) {
      val isInitializerOfVariableDeclarationInStatement =
        node.parent.kind == SyntaxKind.VariableDeclaration &&
        (<VariableDeclaration>node.parent).initializer == node &&
        node.parent.parent.parent.kind == SyntaxKind.VariableStatement

      val variableStatementNode = isInitializerOfVariableDeclarationInStatement ? node.parent.parent.parent : ()
      if (variableStatementNode) {
        return variableStatementNode.jsDocComment
      }

      // Also recognize when the node is the RHS of an assignment expression
      val parent = node.parent
      val isSourceOfAssignmentExpressionStatement =
        parent && parent.parent &&
        parent.kind == SyntaxKind.BinaryExpression &&
        (parent as BinaryExpression).operatorToken.kind == SyntaxKind.EqualsToken &&
        parent.parent.kind == SyntaxKind.ExpressionStatement
      if (isSourceOfAssignmentExpressionStatement) {
        return parent.parent.jsDocComment
      }

      val isPropertyAssignmentExpression = parent && parent.kind == SyntaxKind.PropertyAssignment
      if (isPropertyAssignmentExpression) {
        return parent.jsDocComment
      }
    }

    return ()
  }

  def getJSDocTypeTag(node: Node): JSDocTypeTag {
    return <JSDocTypeTag>getJSDocTag(node, SyntaxKind.JSDocTypeTag, /*checkParentVariableStatement*/ false)
  }

  def getJSDocReturnTag(node: Node): JSDocReturnTag {
    return <JSDocReturnTag>getJSDocTag(node, SyntaxKind.JSDocReturnTag, /*checkParentVariableStatement*/ true)
  }

  def getJSDocTemplateTag(node: Node): JSDocTemplateTag {
    return <JSDocTemplateTag>getJSDocTag(node, SyntaxKind.JSDocTemplateTag, /*checkParentVariableStatement*/ false)
  }

  def getCorrespondingJSDocParameterTag(parameter: ParameterDeclaration): JSDocParameterTag {
    if (parameter.name && parameter.name.kind == SyntaxKind.Identifier) {
      // If it's a parameter, see if the parent has a jsdoc comment with an @param
      // annotation.
      val parameterName = (<Identifier>parameter.name).text

      val jsDocComment = getJSDocComment(parameter.parent, /*checkParentVariableStatement*/ true)
      if (jsDocComment) {
        for (val tag of jsDocComment.tags) {
          if (tag.kind == SyntaxKind.JSDocParameterTag) {
            val parameterTag = <JSDocParameterTag>tag
            val name = parameterTag.preParameterName || parameterTag.postParameterName
            if (name.text == parameterName) {
              return parameterTag
            }
          }
        }
      }
    }

    return ()
  }

  def hasRestParameter(s: SignatureDeclaration): Boolean {
    return isRestParameter(lastOrUndefined(s.parameters))
  }

  def isRestParameter(node: ParameterDeclaration) {
    if (node) {
      if (node.flags & NodeFlags.JavaScriptFile) {
        if (node.type && node.type.kind == SyntaxKind.JSDocVariadicType) {
          return true
        }

        val paramTag = getCorrespondingJSDocParameterTag(node)
        if (paramTag && paramTag.typeExpression) {
          return paramTag.typeExpression.type.kind == SyntaxKind.JSDocVariadicType
        }
      }

      return node.dotDotDotToken != ()
    }

    return false
  }

  def isLiteralKind(kind: SyntaxKind): Boolean {
    return SyntaxKind.FirstLiteralToken <= kind && kind <= SyntaxKind.LastLiteralToken
  }

  def isTextualLiteralKind(kind: SyntaxKind): Boolean {
    return kind == SyntaxKind.StringLiteral || kind == SyntaxKind.NoSubstitutionTemplateLiteral
  }

  def isTemplateLiteralKind(kind: SyntaxKind): Boolean {
    return SyntaxKind.FirstTemplateToken <= kind && kind <= SyntaxKind.LastTemplateToken
  }

  def isBindingPattern(node: Node): node is BindingPattern {
    return !!node && (node.kind == SyntaxKind.ArrayBindingPattern || node.kind == SyntaxKind.ObjectBindingPattern)
  }

  def isNodeDescendentOf(node: Node, ancestor: Node): Boolean {
    while (node) {
      if (node == ancestor) return true
      node = node.parent
    }
    return false
  }

  def isInAmbientContext(node: Node): Boolean {
    while (node) {
      if (node.flags & NodeFlags.Ambient || (node.kind == SyntaxKind.SourceFile && (node as SourceFile).isDeclarationFile)) {
        return true
      }
      node = node.parent
    }
    return false
  }

  def isDeclaration(node: Node): Boolean {
    switch (node.kind) {
      case SyntaxKind.ArrowFunction:
      case SyntaxKind.BindingElement:
      case SyntaxKind.ClassDeclaration:
      case SyntaxKind.ClassExpression:
      case SyntaxKind.Constructor:
      case SyntaxKind.EnumDeclaration:
      case SyntaxKind.EnumMember:
      case SyntaxKind.ExportSpecifier:
      case SyntaxKind.FunctionDeclaration:
      case SyntaxKind.FunctionExpression:
      case SyntaxKind.GetAccessor:
      case SyntaxKind.ImportClause:
      case SyntaxKind.ImportEqualsDeclaration:
      case SyntaxKind.ImportSpecifier:
      case SyntaxKind.InterfaceDeclaration:
      case SyntaxKind.MethodDeclaration:
      case SyntaxKind.MethodSignature:
      case SyntaxKind.ModuleDeclaration:
      case SyntaxKind.NamespaceImport:
      case SyntaxKind.Parameter:
      case SyntaxKind.PropertyAssignment:
      case SyntaxKind.PropertyDeclaration:
      case SyntaxKind.PropertySignature:
      case SyntaxKind.SetAccessor:
      case SyntaxKind.ShorthandPropertyAssignment:
      case SyntaxKind.TypeAliasDeclaration:
      case SyntaxKind.TypeParameter:
      case SyntaxKind.VariableDeclaration:
        return true
    }
    return false
  }

  def isStatement(n: Node): Boolean {
    switch (n.kind) {
      case SyntaxKind.BreakStatement:
      case SyntaxKind.ContinueStatement:
      case SyntaxKind.DebuggerStatement:
      case SyntaxKind.DoStatement:
      case SyntaxKind.ExpressionStatement:
      case SyntaxKind.EmptyStatement:
      case SyntaxKind.ForInStatement:
      case SyntaxKind.ForOfStatement:
      case SyntaxKind.ForStatement:
      case SyntaxKind.IfStatement:
      case SyntaxKind.LabeledStatement:
      case SyntaxKind.ReturnStatement:
      case SyntaxKind.SwitchStatement:
      case SyntaxKind.ThrowStatement:
      case SyntaxKind.TryStatement:
      case SyntaxKind.VariableStatement:
      case SyntaxKind.WhileStatement:
      case SyntaxKind.WithStatement:
      case SyntaxKind.ExportAssignment:
        return true
      default:
        return false
    }
  }

  def isClassElement(n: Node): Boolean {
    switch (n.kind) {
      case SyntaxKind.Constructor:
      case SyntaxKind.PropertyDeclaration:
      case SyntaxKind.MethodDeclaration:
      case SyntaxKind.GetAccessor:
      case SyntaxKind.SetAccessor:
      case SyntaxKind.MethodSignature:
      case SyntaxKind.IndexSignature:
        return true
      default:
        return false
    }
  }

  // True if the given identifier, String literal, or Int literal is the name of a declaration node
  def isDeclarationName(name: Node): name is Identifier | StringLiteral | LiteralExpression {
    if (name.kind != SyntaxKind.Identifier && name.kind != SyntaxKind.StringLiteral && name.kind != SyntaxKind.NumericLiteral) {
      return false
    }

    val parent = name.parent
    if (parent.kind == SyntaxKind.ImportSpecifier || parent.kind == SyntaxKind.ExportSpecifier) {
      if ((<ImportOrExportSpecifier>parent).propertyName) {
        return true
      }
    }

    if (isDeclaration(parent)) {
      return (<Declaration>parent).name == name
    }

    return false
  }

  // Return true if the given identifier is classified as an IdentifierName
  def isIdentifierName(node: Identifier): Boolean {
    var parent = node.parent
    switch (parent.kind) {
      case SyntaxKind.PropertyDeclaration:
      case SyntaxKind.PropertySignature:
      case SyntaxKind.MethodDeclaration:
      case SyntaxKind.MethodSignature:
      case SyntaxKind.GetAccessor:
      case SyntaxKind.SetAccessor:
      case SyntaxKind.EnumMember:
      case SyntaxKind.PropertyAssignment:
      case SyntaxKind.PropertyAccessExpression:
        // Name in member declaration or property name in property access
        return (<Declaration | PropertyAccessExpression>parent).name == node
      case SyntaxKind.QualifiedName:
        // Name on right hand side of dot in a type query
        if ((<QualifiedName>parent).right == node) {
          while (parent.kind == SyntaxKind.QualifiedName) {
            parent = parent.parent
          }
          return parent.kind == SyntaxKind.TypeQuery
        }
        return false
      case SyntaxKind.BindingElement:
      case SyntaxKind.ImportSpecifier:
        // Property name in binding element or import specifier
        return (<BindingElement | ImportSpecifier>parent).propertyName == node
      case SyntaxKind.ExportSpecifier:
        // Any name in an specifier
        return true
    }
    return false
  }

  // An alias symbol is created by one of the following declarations:
  // import <symbol> = ...
  // import <symbol> from ...
  // import * as <symbol> from ...
  // import { x as <symbol> } from ...
  // { x as <symbol> } from ...
  // = ...
  // default ...
  def isAliasSymbolDeclaration(node: Node): Boolean {
    return node.kind == SyntaxKind.ImportEqualsDeclaration ||
      node.kind == SyntaxKind.ImportClause && !!(<ImportClause>node).name ||
      node.kind == SyntaxKind.NamespaceImport ||
      node.kind == SyntaxKind.ImportSpecifier ||
      node.kind == SyntaxKind.ExportSpecifier ||
      node.kind == SyntaxKind.ExportAssignment && (<ExportAssignment>node).expression.kind == SyntaxKind.Identifier
  }

  def getClassExtendsHeritageClauseElement(node: ClassLikeDeclaration) {
    val heritageClause = getHeritageClause(node.heritageClauses, SyntaxKind.ExtendsKeyword)
    return heritageClause && heritageClause.types.length > 0 ? heritageClause.types[0] : ()
  }

  def getClassImplementsHeritageClauseElements(node: ClassLikeDeclaration) {
    val heritageClause = getHeritageClause(node.heritageClauses, SyntaxKind.ImplementsKeyword)
    return heritageClause ? heritageClause.types : ()
  }

  def getInterfaceBaseTypeNodes(node: InterfaceDeclaration) {
    val heritageClause = getHeritageClause(node.heritageClauses, SyntaxKind.ExtendsKeyword)
    return heritageClause ? heritageClause.types : ()
  }

  def getHeritageClause(clauses: NodeArray<HeritageClause>, kind: SyntaxKind) {
    if (clauses) {
      for (val clause of clauses) {
        if (clause.token == kind) {
          return clause
        }
      }
    }

    return ()
  }

  def tryResolveScriptReference(host: ScriptReferenceHost, sourceFile: SourceFile, reference: FileReference) {
    if (!host.getCompilerOptions().noResolve) {
      val referenceFileName = isRootedDiskPath(reference.fileName) ? reference.fileName : combinePaths(getDirectoryPath(sourceFile.fileName), reference.fileName)
      return host.getSourceFile(referenceFileName)
    }
  }

  def getAncestor(node: Node, kind: SyntaxKind): Node {
    while (node) {
      if (node.kind == kind) {
        return node
      }
      node = node.parent
    }
    return ()
  }

  def getFileReferenceFromReferencePath(comment: String, commentRange: CommentRange): ReferencePathMatchResult {
    val simpleReferenceRegEx = /^\/\/\/\s*<reference\s+/gim
    val isNoDefaultLibRegEx = /^(\/\/\/\s*<reference\s+no-default-lib\s*=\s*)('|")(.+?)\2\s*\/>/gim
    if (simpleReferenceRegEx.test(comment)) {
      if (isNoDefaultLibRegEx.test(comment)) {
        return {
          isNoDefaultLib: true
        }
      }
      else {
        val matchResult = fullTripleSlashReferencePathRegEx.exec(comment)
        if (matchResult) {
          val start = commentRange.pos
          val end = commentRange.end
          return {
            fileReference: {
              pos: start,
              end: end,
              fileName: matchResult[3]
            },
            isNoDefaultLib: false
          }
        }
        else {
          return {
            diagnosticMessage: Diagnostics.Invalid_reference_directive_syntax,
            isNoDefaultLib: false
          }
        }
      }
    }

    return ()
  }

  def isKeyword(token: SyntaxKind): Boolean {
    return SyntaxKind.FirstKeyword <= token && token <= SyntaxKind.LastKeyword
  }

  def isTrivia(token: SyntaxKind) {
    return SyntaxKind.FirstTriviaToken <= token && token <= SyntaxKind.LastTriviaToken
  }

  def isAsyncFunctionLike(node: Node): Boolean {
    return isFunctionLike(node) && (node.flags & NodeFlags.Async) != 0 && !isAccessor(node)
  }

  def isStringOrNumericLiteral(kind: SyntaxKind): Boolean {
    return kind == SyntaxKind.StringLiteral || kind == SyntaxKind.NumericLiteral
  }

  /**
   * A declaration has a dynamic name if both of the following are true:
   *   1. The declaration has a computed property name
   *   2. The computed name is *not* expressed as Symbol.<name>, where name
   *    is a property of the Symbol constructor that denotes a built in
   *    Symbol.
   */
  def hasDynamicName(declaration: Declaration): Boolean {
    return declaration.name && isDynamicName(declaration.name)
  }

  def isDynamicName(name: DeclarationName): Boolean {
    return name.kind == SyntaxKind.ComputedPropertyName &&
      !isStringOrNumericLiteral((<ComputedPropertyName>name).expression.kind) &&
      !isWellKnownSymbolSyntactically((<ComputedPropertyName>name).expression)
  }

  /**
   * Checks if the expression is of the form:
   *  Symbol.name
   * where Symbol is literally the word "Symbol", and name is any identifierName
   */
  def isWellKnownSymbolSyntactically(node: Expression): Boolean {
    return isPropertyAccessExpression(node) && isESSymbolIdentifier(node.expression)
  }

  def getPropertyNameForPropertyNameNode(name: DeclarationName): String {
    if (name.kind == SyntaxKind.Identifier || name.kind == SyntaxKind.StringLiteral || name.kind == SyntaxKind.NumericLiteral) {
      return (<Identifier | LiteralExpression>name).text
    }
    if (name.kind == SyntaxKind.ComputedPropertyName) {
      val nameExpression = (<ComputedPropertyName>name).expression
      if (isWellKnownSymbolSyntactically(nameExpression)) {
        val rightHandSideName = (<PropertyAccessExpression>nameExpression).name.text
        return getPropertyNameForKnownSymbolName(rightHandSideName)
      }
    }

    return ()
  }

  def getPropertyNameForKnownSymbolName(symbolName: String): String {
    return "__@" + symbolName
  }

  /**
   * Includes the word "Symbol" with unicode escapes
   */
  def isESSymbolIdentifier(node: Node): Boolean {
    return node.kind == SyntaxKind.Identifier && (<Identifier>node).text == "Symbol"
  }

  def isModifierKind(token: SyntaxKind): Boolean {
    switch (token) {
      case SyntaxKind.AbstractKeyword:
      case SyntaxKind.AsyncKeyword:
      case SyntaxKind.ConstKeyword:
      case SyntaxKind.DeclareKeyword:
      case SyntaxKind.DefaultKeyword:
      case SyntaxKind.ExportKeyword:
      case SyntaxKind.PublicKeyword:
      case SyntaxKind.PrivateKeyword:
      case SyntaxKind.ProtectedKeyword:
      case SyntaxKind.ReadonlyKeyword:
      case SyntaxKind.StaticKeyword:
        return true
    }
    return false
  }

  def isParameterDeclaration(node: VariableLikeDeclaration) {
    val root = getRootDeclaration(node)
    return root.kind == SyntaxKind.Parameter
  }

  def getRootDeclaration(node: Node): Node {
    while (node.kind == SyntaxKind.BindingElement) {
      node = node.parent.parent
    }
    return node
  }

  def nodeStartsNewLexicalEnvironment(n: Node): Boolean {
    return isFunctionLike(n) || n.kind == SyntaxKind.ModuleDeclaration || n.kind == SyntaxKind.SourceFile
  }

  /**
   * Creates a shallow, memberwise clone of a node. The "kind", "pos", "end", "flags", and "parent"
   * properties are excluded by default, and can be provided via the "location", "flags", and
   * "parent" parameters.
   * @param node The node to clone.
   * @param location An optional TextRange to use to supply the new position.
   * @param flags The NodeFlags to use for the cloned node.
   * @param parent The parent for the new node.
   */
  def cloneNode<T extends Node>(node: T, location?: TextRange, flags?: NodeFlags, parent?: Node): T {
    // We don't use "clone" from core.ts here, as we need to preserve the prototype chain of
    // the original node. We also need to exclude specific properties and only include own-
    // properties (to skip members already defined on the shared prototype).
    val clone = location != ()
      ? <T>createNode(node.kind, location.pos, location.end)
      : <T>createSynthesizedNode(node.kind)

    for (val key in node) {
      if (clone.hasOwnProperty(key) || !node.hasOwnProperty(key)) {
        continue
      }

      (<any>clone)[key] = (<any>node)[key]
    }

    if (flags != ()) {
      clone.flags = flags
    }

    if (parent != ()) {
      clone.parent = parent
    }

    return clone
  }

  /**
   * Creates a deep clone of an EntityName, with new parent pointers.
   * @param node The EntityName to clone.
   * @param parent The parent for the cloned node.
   */
  def cloneEntityName(node: EntityName, parent?: Node): EntityName {
    val clone = cloneNode(node, node, node.flags, parent)
    if (isQualifiedName(clone)) {
      val { left, right } = clone
      clone.left = cloneEntityName(left, clone)
      clone.right = cloneNode(right, right, right.flags, parent)
    }

    return clone
  }

  def isQualifiedName(node: Node): node is QualifiedName {
    return node.kind == SyntaxKind.QualifiedName
  }

  def nodeIsSynthesized(node: Node): Boolean {
    return node.pos == -1
  }

  def createSynthesizedNode(kind: SyntaxKind, startsOnNewLine?: Boolean): Node {
    val node = <SynthesizedNode>createNode(kind, /* pos */ -1, /* end */ -1)
    node.startsOnNewLine = startsOnNewLine
    return node
  }

  def createSynthesizedNodeArray(): NodeArray<any> {
    val array = <NodeArray<any>>[]
    array.pos = -1
    array.end = -1
    return array
  }

  def createDiagnosticCollection(): DiagnosticCollection {
    var nonFileDiagnostics: Diagnostic[] = []
    val fileDiagnostics: Map<Diagnostic[]> = {}

    var diagnosticsModified = false
    var modificationCount = 0

    return {
      add,
      getGlobalDiagnostics,
      getDiagnostics,
      getModificationCount,
      reattachFileDiagnostics
    }

    def getModificationCount() {
      return modificationCount
    }

    def reattachFileDiagnostics(newFile: SourceFile): Unit {
      if (!hasProperty(fileDiagnostics, newFile.fileName)) {
        return
      }

      for (val diagnostic of fileDiagnostics[newFile.fileName]) {
        diagnostic.file = newFile
      }
    }

    def add(diagnostic: Diagnostic): Unit {
      var diagnostics: Diagnostic[]
      if (diagnostic.file) {
        diagnostics = fileDiagnostics[diagnostic.file.fileName]
        if (!diagnostics) {
          diagnostics = []
          fileDiagnostics[diagnostic.file.fileName] = diagnostics
        }
      }
      else {
        diagnostics = nonFileDiagnostics
      }

      diagnostics.push(diagnostic)
      diagnosticsModified = true
      modificationCount++
    }

    def getGlobalDiagnostics(): Diagnostic[] {
      sortAndDeduplicate()
      return nonFileDiagnostics
    }

    def getDiagnostics(fileName?: String): Diagnostic[] {
      sortAndDeduplicate()
      if (fileName) {
        return fileDiagnostics[fileName] || []
      }

      val allDiagnostics: Diagnostic[] = []
      def pushDiagnostic(d: Diagnostic) {
        allDiagnostics.push(d)
      }

      forEach(nonFileDiagnostics, pushDiagnostic)

      for (val key in fileDiagnostics) {
        if (hasProperty(fileDiagnostics, key)) {
          forEach(fileDiagnostics[key], pushDiagnostic)
        }
      }

      return sortAndDeduplicateDiagnostics(allDiagnostics)
    }

    def sortAndDeduplicate() {
      if (!diagnosticsModified) {
        return
      }

      diagnosticsModified = false
      nonFileDiagnostics = sortAndDeduplicateDiagnostics(nonFileDiagnostics)

      for (val key in fileDiagnostics) {
        if (hasProperty(fileDiagnostics, key)) {
          fileDiagnostics[key] = sortAndDeduplicateDiagnostics(fileDiagnostics[key])
        }
      }
    }
  }

  // This consists of the first 19 unprintable ASCII characters, canonical escapes, lineSeparator,
  // paragraphSeparator, and nextLine. The latter three are just desirable to suppress new lines in
  // the language service. These characters should be escaped when printing, and if any characters are added,
  // the map below must be updated. Note that this regexp *does not* include the 'delete' character.
  // There is no reason for this other than that JSON.stringify does not handle it either.
  val escapedCharsRegExp = /[\\\"\u0000-\u001f\t\v\f\b\r\n\u2028\u2029\u0085]/g
  val escapedCharsMap: Map<String> = {
    "\0": "\\0",
    "\t": "\\t",
    "\v": "\\v",
    "\f": "\\f",
    "\b": "\\b",
    "\r": "\\r",
    "\n": "\\n",
    "\\": "\\\\",
    "\"": "\\\"",
    "\u2028": "\\u2028", // lineSeparator
    "\u2029": "\\u2029", // paragraphSeparator
    "\u0085": "\\u0085"  // nextLine
  }


  /**
   * Based heavily on the abstract 'Quote'/'QuoteJSONString' operation from ECMA-262 (24.3.2.2),
   * but augmented for a few select characters (e.g. lineSeparator, paragraphSeparator, nextLine)
   * Note that this doesn't actually wrap the input in double quotes.
   */
  def escapeString(s: String): String {
    s = escapedCharsRegExp.test(s) ? s.replace(escapedCharsRegExp, getReplacement) : s

    return s

    def getReplacement(c: String) {
      return escapedCharsMap[c] || get16BitUnicodeEscapeSequence(c.charCodeAt(0))
    }
  }

  def isIntrinsicJsxName(name: String) {
    val ch = name.substr(0, 1)
    return ch.toLowerCase() == ch
  }

  def get16BitUnicodeEscapeSequence(charCode: Int): String {
    val hexCharCode = charCode.toString(16).toUpperCase()
    val paddedHexCode = ("0000" + hexCharCode).slice(-4)
    return "\\u" + paddedHexCode
  }

  val nonAsciiCharacters = /[^\u0000-\u007F]/g
  def escapeNonAsciiCharacters(s: String): String {
    // Replace non-ASCII characters with '\uNNNN' escapes if any exist.
    // Otherwise just return the original String.
    return nonAsciiCharacters.test(s) ?
      s.replace(nonAsciiCharacters, c => get16BitUnicodeEscapeSequence(c.charCodeAt(0))) :
      s
  }

  trait EmitTextWriter {
    write(s: String): Unit
    writeTextOfNode(text: String, node: Node): Unit
    writeLine(): Unit
    increaseIndent(): Unit
    decreaseIndent(): Unit
    getText(): String
    rawWrite(s: String): Unit
    writeLiteral(s: String): Unit
    getTextPos(): Int
    getLine(): Int
    getColumn(): Int
    getIndent(): Int
    reset(): Unit
  }

  val indentStrings: String[] = ["", "  "]
  def getIndentString(level: Int) {
    if (indentStrings[level] == ()) {
      indentStrings[level] = getIndentString(level - 1) + indentStrings[1]
    }
    return indentStrings[level]
  }

  def getIndentSize() {
    return indentStrings[1].length
  }

  def createTextWriter(newLine: String): EmitTextWriter {
    var output: String
    var indent: Int
    var lineStart: Boolean
    var lineCount: Int
    var linePos: Int

    def write(s: String) {
      if (s && s.length) {
        if (lineStart) {
          output += getIndentString(indent)
          lineStart = false
        }
        output += s
      }
    }

    def reset(): Unit {
      output = ""
      indent = 0
      lineStart = true
      lineCount = 0
      linePos = 0
    }

    def rawWrite(s: String) {
      if (s != ()) {
        if (lineStart) {
          lineStart = false
        }
        output += s
      }
    }

    def writeLiteral(s: String) {
      if (s && s.length) {
        write(s)
        val lineStartsOfS = computeLineStarts(s)
        if (lineStartsOfS.length > 1) {
          lineCount = lineCount + lineStartsOfS.length - 1
          linePos = output.length - s.length + lastOrUndefined(lineStartsOfS)
        }
      }
    }

    def writeLine() {
      if (!lineStart) {
        output += newLine
        lineCount++
        linePos = output.length
        lineStart = true
      }
    }

    def writeTextOfNode(text: String, node: Node) {
      write(getTextOfNodeFromSourceText(text, node))
    }

    reset()

    return {
      write,
      rawWrite,
      writeTextOfNode,
      writeLiteral,
      writeLine,
      increaseIndent: () => { indent++; },
      decreaseIndent: () => { indent--; },
      getIndent: () => indent,
      getTextPos: () => output.length,
      getLine: () => lineCount + 1,
      getColumn: () => lineStart ? indent * getIndentSize() + 1 : output.length - linePos + 1,
      getText: () => output,
      reset
    }
  }

  /**
   * Resolves a local path to a path which is absolute to the base of the emit
   */
  def getExternalModuleNameFromPath(host: EmitHost, fileName: String): String {
    val getCanonicalFileName = (f: String) => host.getCanonicalFileName(f)
    val dir = toPath(host.getCommonSourceDirectory(), host.getCurrentDirectory(), getCanonicalFileName)
    val filePath = getNormalizedAbsolutePath(fileName, host.getCurrentDirectory())
    val relativePath = getRelativePathToDirectoryOrUrl(dir, filePath, dir, getCanonicalFileName, /*isAbsolutePathAnUrl*/ false)
    return removeFileExtension(relativePath)
  }

  def getOwnEmitOutputFilePath(sourceFile: SourceFile, host: EmitHost, extension: String) {
    val compilerOptions = host.getCompilerOptions()
    var emitOutputFilePathWithoutExtension: String
    if (compilerOptions.outDir) {
      emitOutputFilePathWithoutExtension = removeFileExtension(getSourceFilePathInNewDir(sourceFile, host, compilerOptions.outDir))
    }
    else {
      emitOutputFilePathWithoutExtension = removeFileExtension(sourceFile.fileName)
    }

    return emitOutputFilePathWithoutExtension + extension
  }

  def getEmitScriptTarget(compilerOptions: CompilerOptions) {
    return compilerOptions.target || ScriptTarget.ES3
  }

  def getEmitModuleKind(compilerOptions: CompilerOptions) {
    return typeof compilerOptions.module == "Int" ?
      compilerOptions.module :
      getEmitScriptTarget(compilerOptions) == ScriptTarget.ES6 ? ModuleKind.ES6 : ModuleKind.CommonJS
  }

  trait EmitFileNames {
    jsFilePath: String
    sourceMapFilePath: String
    declarationFilePath: String
  }

  def forEachExpectedEmitFile(host: EmitHost,
    action: (emitFileNames: EmitFileNames, sourceFiles: SourceFile[], isBundledEmit: Boolean) => Unit,
    targetSourceFile?: SourceFile) {
    val options = host.getCompilerOptions()
    // Emit on each source file
    if (options.outFile || options.out) {
      onBundledEmit(host)
    }
    else {
      val sourceFiles = targetSourceFile == () ? host.getSourceFiles() : [targetSourceFile]
      for (val sourceFile of sourceFiles) {
        if (!isDeclarationFile(sourceFile)) {
          onSingleFileEmit(host, sourceFile)
        }
      }
    }

    def onSingleFileEmit(host: EmitHost, sourceFile: SourceFile) {
      // JavaScript files are always LanguageVariant.JSX, as JSX syntax is allowed in .js files also.
      // So for JavaScript files, '.jsx' is only emitted if the input was '.jsx', and JsxEmit.Preserve.
      // For TypeScript, the only time to emit with a '.jsx' extension, is on JSX input, and JsxEmit.Preserve
      var extension = ".js"
      if (options.jsx == JsxEmit.Preserve) {
        if (isSourceFileJavaScript(sourceFile)) {
          if (fileExtensionIs(sourceFile.fileName, ".jsx")) {
            extension = ".jsx"
          }
        }
        else if (sourceFile.languageVariant == LanguageVariant.JSX) {
          // TypeScript source file preserving JSX syntax
          extension = ".jsx"
        }
      }
      val jsFilePath = getOwnEmitOutputFilePath(sourceFile, host, extension)
      val emitFileNames: EmitFileNames = {
        jsFilePath,
        sourceMapFilePath: getSourceMapFilePath(jsFilePath, options),
        declarationFilePath: !isSourceFileJavaScript(sourceFile) ? getDeclarationEmitFilePath(jsFilePath, options) : ()
      }
      action(emitFileNames, [sourceFile], /*isBundledEmit*/false)
    }

    def onBundledEmit(host: EmitHost) {
      // Can emit only sources that are not declaration file and are either non module code or module with --module or --target es6 specified
      val bundledSources = filter(host.getSourceFiles(),
        sourceFile => !isDeclarationFile(sourceFile) && // Not a declaration file
          (!isExternalModule(sourceFile) || // non module file
            (getEmitModuleKind(options) && isExternalModule(sourceFile)))); // module that can emit - note falsy value from getEmitModuleKind means the module kind that shouldn't be emitted
      if (bundledSources.length) {
        val jsFilePath = options.outFile || options.out
        val emitFileNames: EmitFileNames = {
          jsFilePath,
          sourceMapFilePath: getSourceMapFilePath(jsFilePath, options),
          declarationFilePath: getDeclarationEmitFilePath(jsFilePath, options)
        }
        action(emitFileNames, bundledSources, /*isBundledEmit*/true)
      }
    }

    def getSourceMapFilePath(jsFilePath: String, options: CompilerOptions) {
      return options.sourceMap ? jsFilePath + ".map" : ()
    }

    def getDeclarationEmitFilePath(jsFilePath: String, options: CompilerOptions) {
      return options.declaration ? removeFileExtension(jsFilePath) + ".d.ts" : ()
    }
  }

  def getSourceFilePathInNewDir(sourceFile: SourceFile, host: EmitHost, newDirPath: String) {
    var sourceFilePath = getNormalizedAbsolutePath(sourceFile.fileName, host.getCurrentDirectory())
    sourceFilePath = sourceFilePath.replace(host.getCommonSourceDirectory(), "")
    return combinePaths(newDirPath, sourceFilePath)
  }

  def writeFile(host: EmitHost, diagnostics: DiagnosticCollection, fileName: String, data: String, writeByteOrderMark: Boolean) {
    host.writeFile(fileName, data, writeByteOrderMark, hostErrorMessage => {
      diagnostics.add(createCompilerDiagnostic(Diagnostics.Could_not_write_file_0_Colon_1, fileName, hostErrorMessage))
    })
  }

  def getLineOfLocalPosition(currentSourceFile: SourceFile, pos: Int) {
    return getLineAndCharacterOfPosition(currentSourceFile, pos).line
  }

  def getLineOfLocalPositionFromLineMap(lineMap: Int[], pos: Int) {
    return computeLineAndCharacterOfPosition(lineMap, pos).line
  }

  def getFirstConstructorWithBody(node: ClassLikeDeclaration): ConstructorDeclaration {
    return forEach(node.members, member => {
      if (member.kind == SyntaxKind.Constructor && nodeIsPresent((<ConstructorDeclaration>member).body)) {
        return <ConstructorDeclaration>member
      }
    })
  }

  def getSetAccessorTypeAnnotationNode(accessor: AccessorDeclaration): TypeNode {
    return accessor && accessor.parameters.length > 0 && accessor.parameters[0].type
  }

  def getAllAccessorDeclarations(declarations: NodeArray<Declaration>, accessor: AccessorDeclaration) {
    var firstAccessor: AccessorDeclaration
    var secondAccessor: AccessorDeclaration
    var getAccessor: AccessorDeclaration
    var setAccessor: AccessorDeclaration
    if (hasDynamicName(accessor)) {
      firstAccessor = accessor
      if (accessor.kind == SyntaxKind.GetAccessor) {
        getAccessor = accessor
      }
      else if (accessor.kind == SyntaxKind.SetAccessor) {
        setAccessor = accessor
      }
      else {
        Debug.fail("Accessor has wrong kind")
      }
    }
    else {
      forEach(declarations, (member: Declaration) => {
        if ((member.kind == SyntaxKind.GetAccessor || member.kind == SyntaxKind.SetAccessor)
          && (member.flags & NodeFlags.Static) == (accessor.flags & NodeFlags.Static)) {
          val memberName = getPropertyNameForPropertyNameNode(member.name)
          val accessorName = getPropertyNameForPropertyNameNode(accessor.name)
          if (memberName == accessorName) {
            if (!firstAccessor) {
              firstAccessor = <AccessorDeclaration>member
            }
            else if (!secondAccessor) {
              secondAccessor = <AccessorDeclaration>member
            }

            if (member.kind == SyntaxKind.GetAccessor && !getAccessor) {
              getAccessor = <AccessorDeclaration>member
            }

            if (member.kind == SyntaxKind.SetAccessor && !setAccessor) {
              setAccessor = <AccessorDeclaration>member
            }
          }
        }
      })
    }
    return {
      firstAccessor,
      secondAccessor,
      getAccessor,
      setAccessor
    }
  }

  def emitNewLineBeforeLeadingComments(lineMap: Int[], writer: EmitTextWriter, node: TextRange, leadingComments: CommentRange[]) {
    // If the leading comments start on different line than the start of node, write new line
    if (leadingComments && leadingComments.length && node.pos != leadingComments[0].pos &&
      getLineOfLocalPositionFromLineMap(lineMap, node.pos) != getLineOfLocalPositionFromLineMap(lineMap, leadingComments[0].pos)) {
      writer.writeLine()
    }
  }

  def emitComments(text: String, lineMap: Int[], writer: EmitTextWriter, comments: CommentRange[], trailingSeparator: Boolean, newLine: String,
    writeComment: (text: String, lineMap: Int[], writer: EmitTextWriter, comment: CommentRange, newLine: String) => Unit) {
    var emitLeadingSpace = !trailingSeparator
    forEach(comments, comment => {
      if (emitLeadingSpace) {
        writer.write(" ")
        emitLeadingSpace = false
      }
      writeComment(text, lineMap, writer, comment, newLine)
      if (comment.hasTrailingNewLine) {
        writer.writeLine()
      }
      else if (trailingSeparator) {
        writer.write(" ")
      }
      else {
        // Emit leading space to separate comment during next comment emit
        emitLeadingSpace = true
      }
    })
  }

  /**
   * Detached comment is a comment at the top of file or def body that is separated from
   * the next statement by space.
   */
  def emitDetachedComments(text: String, lineMap: Int[], writer: EmitTextWriter,
    writeComment: (text: String, lineMap: Int[], writer: EmitTextWriter, comment: CommentRange, newLine: String) => Unit,
    node: TextRange, newLine: String, removeComments: Boolean) {
    var leadingComments: CommentRange[]
    var currentDetachedCommentInfo: {nodePos: Int, detachedCommentEndPos: Int}
    if (removeComments) {
      // removeComments is true, only reserve pinned comment at the top of file
      // For example:
      //    /*! Pinned Comment */
      //
      //    var x = 10
      if (node.pos == 0) {
        leadingComments = filter(getLeadingCommentRanges(text, node.pos), isPinnedComment)
      }
    }
    else {
      // removeComments is false, just get detached as normal and bypass the process to filter comment
      leadingComments = getLeadingCommentRanges(text, node.pos)
    }

    if (leadingComments) {
      val detachedComments: CommentRange[] = []
      var lastComment: CommentRange

      for (val comment of leadingComments) {
        if (lastComment) {
          val lastCommentLine = getLineOfLocalPositionFromLineMap(lineMap, lastComment.end)
          val commentLine = getLineOfLocalPositionFromLineMap(lineMap, comment.pos)

          if (commentLine >= lastCommentLine + 2) {
            // There was a blank line between the last comment and this comment.  This
            // comment is not part of the copyright comments.  Return what we have so
            // far.
            break
          }
        }

        detachedComments.push(comment)
        lastComment = comment
      }

      if (detachedComments.length) {
        // All comments look like they could have been part of the copyright header.  Make
        // sure there is at least one blank line between it and the node.  If not, it's not
        // a copyright header.
        val lastCommentLine = getLineOfLocalPositionFromLineMap(lineMap, lastOrUndefined(detachedComments).end)
        val nodeLine = getLineOfLocalPositionFromLineMap(lineMap, skipTrivia(text, node.pos))
        if (nodeLine >= lastCommentLine + 2) {
          // Valid detachedComments
          emitNewLineBeforeLeadingComments(lineMap, writer, node, leadingComments)
          emitComments(text, lineMap, writer, detachedComments, /*trailingSeparator*/ true, newLine, writeComment)
          currentDetachedCommentInfo = { nodePos: node.pos, detachedCommentEndPos: lastOrUndefined(detachedComments).end }
        }
      }
    }

    return currentDetachedCommentInfo

    def isPinnedComment(comment: CommentRange) {
      return text.charCodeAt(comment.pos + 1) == CharacterCodes.asterisk &&
        text.charCodeAt(comment.pos + 2) == CharacterCodes.exclamation
    }

  }

  def writeCommentRange(text: String, lineMap: Int[], writer: EmitTextWriter, comment: CommentRange, newLine: String) {
    if (text.charCodeAt(comment.pos + 1) == CharacterCodes.asterisk) {
      val firstCommentLineAndCharacter = computeLineAndCharacterOfPosition(lineMap, comment.pos)
      val lineCount = lineMap.length
      var firstCommentLineIndent: Int
      for (var pos = comment.pos, currentLine = firstCommentLineAndCharacter.line; pos < comment.end; currentLine++) {
        val nextLineStart = (currentLine + 1) == lineCount
          ? text.length + 1
          : lineMap[currentLine + 1]

        if (pos != comment.pos) {
          // If we are not emitting first line, we need to write the spaces to adjust the alignment
          if (firstCommentLineIndent == ()) {
            firstCommentLineIndent = calculateIndent(text, lineMap[firstCommentLineAndCharacter.line], comment.pos)
          }

          // These are Int of spaces writer is going to write at current indent
          val currentWriterIndentSpacing = writer.getIndent() * getIndentSize()

          // Number of spaces we want to be writing
          // eg: Assume writer indent
          // module m {
          //     /* starts at character 9 this is line 1
          //  * starts at character pos 4 line            --1  = 8 - 8 + 3
          //   More left indented comment */              --2  = 8 - 8 + 2
          //   class c { }
          // }
          // module m {
          //   /* this is line 1 -- Assume current writer indent 8
          //    * line                        --3 = 8 - 4 + 5
          //      More right indented comment */          --4 = 8 - 4 + 11
          //   class c { }
          // }
          val spacesToEmit = currentWriterIndentSpacing - firstCommentLineIndent + calculateIndent(text, pos, nextLineStart)
          if (spacesToEmit > 0) {
            var numberOfSingleSpacesToEmit = spacesToEmit % getIndentSize()
            val indentSizeSpaceString = getIndentString((spacesToEmit - numberOfSingleSpacesToEmit) / getIndentSize())

            // Write indent size String ( in eg 1: = "", 2: "" , 3: String with 8 spaces 4: String with 12 spaces
            writer.rawWrite(indentSizeSpaceString)

            // Emit the single spaces (in eg: 1: 3 spaces, 2: 2 spaces, 3: 1 space, 4: 3 spaces)
            while (numberOfSingleSpacesToEmit) {
              writer.rawWrite(" ")
              numberOfSingleSpacesToEmit--
            }
          }
          else {
            // No spaces to emit write empty String
            writer.rawWrite("")
          }
        }

        // Write the comment line text
        writeTrimmedCurrentLine(text, comment, writer, newLine, pos, nextLineStart)

        pos = nextLineStart
      }
    }
    else {
      // Single line comment of style //....
      writer.write(text.substring(comment.pos, comment.end))
    }
  }

  def writeTrimmedCurrentLine(text: String, comment: CommentRange, writer: EmitTextWriter, newLine: String, pos: Int, nextLineStart: Int) {
    val end = Math.min(comment.end, nextLineStart - 1)
    val currentLineText = text.substring(pos, end).replace(/^\s+|\s+$/g, "")
    if (currentLineText) {
      // trimmed forward and ending spaces text
      writer.write(currentLineText)
      if (end != comment.end) {
        writer.writeLine()
      }
    }
    else {
      // Empty String - make sure we write empty line
      writer.writeLiteral(newLine)
    }
  }

  def calculateIndent(text: String, pos: Int, end: Int) {
    var currentLineIndent = 0
    for (; pos < end && isWhiteSpace(text.charCodeAt(pos)); pos++) {
      if (text.charCodeAt(pos) == CharacterCodes.tab) {
        // Tabs = TabSize = indent size and go to next tabStop
        currentLineIndent += getIndentSize() - (currentLineIndent % getIndentSize())
      }
      else {
        // Single space
        currentLineIndent++
      }
    }

    return currentLineIndent
  }

  def modifierToFlag(token: SyntaxKind): NodeFlags {
    switch (token) {
      case SyntaxKind.StaticKeyword: return NodeFlags.Static
      case SyntaxKind.PublicKeyword: return NodeFlags.Public
      case SyntaxKind.ProtectedKeyword: return NodeFlags.Protected
      case SyntaxKind.PrivateKeyword: return NodeFlags.Private
      case SyntaxKind.AbstractKeyword: return NodeFlags.Abstract
      case SyntaxKind.ExportKeyword: return NodeFlags.Export
      case SyntaxKind.DeclareKeyword: return NodeFlags.Ambient
      case SyntaxKind.ConstKeyword: return NodeFlags.Const
      case SyntaxKind.DefaultKeyword: return NodeFlags.Default
      case SyntaxKind.AsyncKeyword: return NodeFlags.Async
      case SyntaxKind.ReadonlyKeyword: return NodeFlags.Readonly
    }
    return 0
  }

  def isLeftHandSideExpression(expr: Expression): Boolean {
    if (expr) {
      switch (expr.kind) {
        case SyntaxKind.PropertyAccessExpression:
        case SyntaxKind.ElementAccessExpression:
        case SyntaxKind.NewExpression:
        case SyntaxKind.CallExpression:
        case SyntaxKind.JsxElement:
        case SyntaxKind.JsxSelfClosingElement:
        case SyntaxKind.TaggedTemplateExpression:
        case SyntaxKind.ArrayLiteralExpression:
        case SyntaxKind.ParenthesizedExpression:
        case SyntaxKind.ObjectLiteralExpression:
        case SyntaxKind.ClassExpression:
        case SyntaxKind.FunctionExpression:
        case SyntaxKind.Identifier:
        case SyntaxKind.RegularExpressionLiteral:
        case SyntaxKind.NumericLiteral:
        case SyntaxKind.StringLiteral:
        case SyntaxKind.NoSubstitutionTemplateLiteral:
        case SyntaxKind.TemplateExpression:
        case SyntaxKind.FalseKeyword:
        case SyntaxKind.NullKeyword:
        case SyntaxKind.ThisKeyword:
        case SyntaxKind.TrueKeyword:
        case SyntaxKind.SuperKeyword:
          return true
      }
    }

    return false
  }

  def isAssignmentOperator(token: SyntaxKind): Boolean {
    return token >= SyntaxKind.FirstAssignment && token <= SyntaxKind.LastAssignment
  }

  def isExpressionWithTypeArgumentsInClassExtendsClause(node: Node): Boolean {
    return node.kind == SyntaxKind.ExpressionWithTypeArguments &&
      (<HeritageClause>node.parent).token == SyntaxKind.ExtendsKeyword &&
      isClassLike(node.parent.parent)
  }

  // Returns false if this heritage clause element's expression contains something unsupported
  // (i.e. not a name or dotted name).
  def isSupportedExpressionWithTypeArguments(node: ExpressionWithTypeArguments): Boolean {
    return isSupportedExpressionWithTypeArgumentsRest(node.expression)
  }

  def isSupportedExpressionWithTypeArgumentsRest(node: Expression): Boolean {
    if (node.kind == SyntaxKind.Identifier) {
      return true
    }
    else if (isPropertyAccessExpression(node)) {
      return isSupportedExpressionWithTypeArgumentsRest(node.expression)
    }
    else {
      return false
    }
  }

  def isRightSideOfQualifiedNameOrPropertyAccess(node: Node) {
    return (node.parent.kind == SyntaxKind.QualifiedName && (<QualifiedName>node.parent).right == node) ||
      (node.parent.kind == SyntaxKind.PropertyAccessExpression && (<PropertyAccessExpression>node.parent).name == node)
  }

  def isEmptyObjectLiteralOrArrayLiteral(expression: Node): Boolean {
    val kind = expression.kind
    if (kind == SyntaxKind.ObjectLiteralExpression) {
      return (<ObjectLiteralExpression>expression).properties.length == 0
    }
    if (kind == SyntaxKind.ArrayLiteralExpression) {
      return (<ArrayLiteralExpression>expression).elements.length == 0
    }
    return false
  }

  def getLocalSymbolForExportDefault(symbol: Symbol) {
    return symbol && symbol.valueDeclaration && (symbol.valueDeclaration.flags & NodeFlags.Default) ? symbol.valueDeclaration.localSymbol : ()
  }

  def hasJavaScriptFileExtension(fileName: String) {
    return forEach(supportedJavascriptExtensions, extension => fileExtensionIs(fileName, extension))
  }

  /**
   * Replace each instance of non-ascii characters by one, two, three, or four escape sequences
   * representing the UTF-8 encoding of the character, and return the expanded char code list.
   */
  def getExpandedCharCodes(input: String): Int[] {
    val output: Int[] = []
    val length = input.length

    for (var i = 0; i < length; i++) {
      val charCode = input.charCodeAt(i)

      // handel utf8
      if (charCode < 0x80) {
        output.push(charCode)
      }
      else if (charCode < 0x800) {
        output.push((charCode >> 6) | 0B11000000)
        output.push((charCode & 0B00111111) | 0B10000000)
      }
      else if (charCode < 0x10000) {
        output.push((charCode >> 12) | 0B11100000)
        output.push(((charCode >> 6) & 0B00111111) | 0B10000000)
        output.push((charCode & 0B00111111) | 0B10000000)
      }
      else if (charCode < 0x20000) {
        output.push((charCode >> 18) | 0B11110000)
        output.push(((charCode >> 12) & 0B00111111) | 0B10000000)
        output.push(((charCode >> 6) & 0B00111111) | 0B10000000)
        output.push((charCode & 0B00111111) | 0B10000000)
      }
      else {
        Debug.assert(false, "Unexpected code point")
      }
    }

    return output
  }

  /**
   * Serialize an object graph into a JSON String. This is intended only for use on an acyclic graph
   * as the fallback implementation does not check for circular references by default.
   */
  val stringify: (value: any) => String = typeof JSON != "()" && JSON.stringify
    ? JSON.stringify
    : stringifyFallback

  /**
   * Serialize an object graph into a JSON String.
   */
  def stringifyFallback(value: any): String {
    // JSON.stringify returns `()` here, instead of the String "()".
    return value == () ? () : stringifyValue(value)
  }

  def stringifyValue(value: any): String {
    return typeof value == "String" ? `"${escapeString(value)}"`
       : typeof value == "Int" ? isFinite(value) ? String(value) : "null"
       : typeof value == "Boolean" ? value ? "true" : "false"
       : typeof value == "object" && value ? isArray(value) ? cycleCheck(stringifyArray, value) : cycleCheck(stringifyObject, value)
       : /*fallback*/ "null"
  }

  def cycleCheck(cb: (value: any) => String, value: any) {
    Debug.assert(!value.hasOwnProperty("__cycle"), "Converting circular structure to JSON")
    value.__cycle = true
    val result = cb(value)
    delete value.__cycle
    return result
  }

  def stringifyArray(value: any) {
    return `[${reduceLeft(value, stringifyElement, "")}]`
  }

  def stringifyElement(memo: String, value: any) {
    return (memo ? memo + "," : memo) + stringifyValue(value)
  }

  def stringifyObject(value: any) {
    return `{${reduceProperties(value, stringifyProperty, "")}}`
  }

  def stringifyProperty(memo: String, value: any, key: String) {
    return value == () || typeof value == "def" || key == "__cycle" ? memo
       : (memo ? memo + "," : memo) + `"${escapeString(key)}":${stringifyValue(value)}`
  }

  val base64Digits = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="

  /**
   * Converts a String to a base-64 encoded ASCII String.
   */
  def convertToBase64(input: String): String {
    var result = ""
    val charCodes = getExpandedCharCodes(input)
    var i = 0
    val length = charCodes.length
    var byte1: Int, byte2: Int, byte3: Int, byte4: Int

    while (i < length) {
      // Convert every 6-bits in the input 3 character points
      // into a base64 digit
      byte1 = charCodes[i] >> 2
      byte2 = (charCodes[i] & 0B00000011) << 4 | charCodes[i + 1] >> 4
      byte3 = (charCodes[i + 1] & 0B00001111) << 2 | charCodes[i + 2] >> 6
      byte4 = charCodes[i + 2] & 0B00111111

      // We are out of characters in the input, set the extra
      // digits to 64 (padding character).
      if (i + 1 >= length) {
        byte3 = byte4 = 64
      }
      else if (i + 2 >= length) {
        byte4 = 64
      }

      // Write to the output
      result += base64Digits.charAt(byte1) + base64Digits.charAt(byte2) + base64Digits.charAt(byte3) + base64Digits.charAt(byte4)

      i += 3
    }

    return result
  }

  def convertToRelativePath(absoluteOrRelativePath: String, basePath: String, getCanonicalFileName: (path: String) => String): String {
    return !isRootedDiskPath(absoluteOrRelativePath)
      ? absoluteOrRelativePath
      : getRelativePathToDirectoryOrUrl(basePath, absoluteOrRelativePath, basePath, getCanonicalFileName, /* isAbsolutePathAnUrl */ false)
  }

  val carriageReturnLineFeed = "\r\n"
  val lineFeed = "\n"
  def getNewLineCharacter(options: CompilerOptions): String {
    if (options.newLine == NewLineKind.CarriageReturnLineFeed) {
      return carriageReturnLineFeed
    }
    else if (options.newLine == NewLineKind.LineFeed) {
      return lineFeed
    }
    else if (sys) {
      return sys.newLine
    }
    return carriageReturnLineFeed
  }
}

package ts {
  def getDefaultLibFileName(options: CompilerOptions): String {
    return options.target == ScriptTarget.ES6 ? "lib.es6.d.ts" : "lib.d.ts"
  }

  def textSpanEnd(span: TextSpan) {
    return span.start + span.length
  }

  def textSpanIsEmpty(span: TextSpan) {
    return span.length == 0
  }

  def textSpanContainsPosition(span: TextSpan, position: Int) {
    return position >= span.start && position < textSpanEnd(span)
  }

  // Returns true if 'span' contains 'other'.
  def textSpanContainsTextSpan(span: TextSpan, other: TextSpan) {
    return other.start >= span.start && textSpanEnd(other) <= textSpanEnd(span)
  }

  def textSpanOverlapsWith(span: TextSpan, other: TextSpan) {
    val overlapStart = Math.max(span.start, other.start)
    val overlapEnd = Math.min(textSpanEnd(span), textSpanEnd(other))
    return overlapStart < overlapEnd
  }

  def textSpanOverlap(span1: TextSpan, span2: TextSpan) {
    val overlapStart = Math.max(span1.start, span2.start)
    val overlapEnd = Math.min(textSpanEnd(span1), textSpanEnd(span2))
    if (overlapStart < overlapEnd) {
      return createTextSpanFromBounds(overlapStart, overlapEnd)
    }
    return ()
  }

  def textSpanIntersectsWithTextSpan(span: TextSpan, other: TextSpan) {
    return other.start <= textSpanEnd(span) && textSpanEnd(other) >= span.start
  }

  def textSpanIntersectsWith(span: TextSpan, start: Int, length: Int) {
    val end = start + length
    return start <= textSpanEnd(span) && end >= span.start
  }

  def decodedTextSpanIntersectsWith(start1: Int, length1: Int, start2: Int, length2: Int) {
    val end1 = start1 + length1
    val end2 = start2 + length2
    return start2 <= end1 && end2 >= start1
  }

  def textSpanIntersectsWithPosition(span: TextSpan, position: Int) {
    return position <= textSpanEnd(span) && position >= span.start
  }

  def textSpanIntersection(span1: TextSpan, span2: TextSpan) {
    val intersectStart = Math.max(span1.start, span2.start)
    val intersectEnd = Math.min(textSpanEnd(span1), textSpanEnd(span2))
    if (intersectStart <= intersectEnd) {
      return createTextSpanFromBounds(intersectStart, intersectEnd)
    }
    return ()
  }

  def createTextSpan(start: Int, length: Int): TextSpan {
    if (start < 0) {
      throw new Error("start < 0")
    }
    if (length < 0) {
      throw new Error("length < 0")
    }

    return { start, length }
  }

  def createTextSpanFromBounds(start: Int, end: Int) {
    return createTextSpan(start, end - start)
  }

  def textChangeRangeNewSpan(range: TextChangeRange) {
    return createTextSpan(range.span.start, range.newLength)
  }

  def textChangeRangeIsUnchanged(range: TextChangeRange) {
    return textSpanIsEmpty(range.span) && range.newLength == 0
  }

  def createTextChangeRange(span: TextSpan, newLength: Int): TextChangeRange {
    if (newLength < 0) {
      throw new Error("newLength < 0")
    }

    return { span, newLength }
  }

  var unchangedTextChangeRange = createTextChangeRange(createTextSpan(0, 0), 0)

  /**
   * Called to merge all the changes that occurred across several versions of a script snapshot
   * into a single change.  i.e. if a user keeps making successive edits to a script we will
   * have a text change from V1 to V2, V2 to V3, ..., Vn.
   *
   * This def will then merge those changes into a single change range valid between V1 and
   * Vn.
   */
  def collapseTextChangeRangesAcrossMultipleVersions(changes: TextChangeRange[]): TextChangeRange {
    if (changes.length == 0) {
      return unchangedTextChangeRange
    }

    if (changes.length == 1) {
      return changes[0]
    }

    // We change from talking about { { oldStart, oldLength }, newLength } to { oldStart, oldEnd, newEnd }
    // as it makes things much easier to reason about.
    val change0 = changes[0]

    var oldStartN = change0.span.start
    var oldEndN = textSpanEnd(change0.span)
    var newEndN = oldStartN + change0.newLength

    for (var i = 1; i < changes.length; i++) {
      val nextChange = changes[i]

      // Consider the following case:
      // i.e. two edits.  The first represents the text change range { { 10, 50 }, 30 }.  i.e. The span starting
      // at 10, with length 50 is reduced to length 30.  The second represents the text change range { { 30, 30 }, 40 }.
      // i.e. the span starting at 30 with length 30 is increased to length 40.
      //
      //    0     10    20    30    40    50    60    70    80    90    100
      //    -------------------------------------------------------------------------------------------------------
      //        |                         /
      //        |                      /----
      //  T1      |                     /----
      //        |                  /----
      //        |               /----
      //    -------------------------------------------------------------------------------------------------------
      //                   |              \
      //                   |                 \
      //   T2                |                 \
      //                   |                   \
      //                   |                    \
      //    -------------------------------------------------------------------------------------------------------
      //
      // Merging these turns out to not be too difficult.  First, determining the new start of the change is trivial
      // it's just the min of the old and new starts.  i.e.:
      //
      //    0     10    20    30    40    50    60    70    80    90    100
      //    ------------------------------------------------------------*------------------------------------------
      //        |                         /
      //        |                      /----
      //  T1      |                     /----
      //        |                  /----
      //        |               /----
      //    ----------------------------------------$-------------------$------------------------------------------
      //        .          |              \
      //        .          |                 \
      //   T2       .          |                 \
      //        .          |                   \
      //        .          |                    \
      //    ----------------------------------------------------------------------*--------------------------------
      //
      // (Note the dots represent the newly inferred start.
      // Determining the new and old end is also pretty simple.  Basically it boils down to paying attention to the
      // absolute positions at the asterisks, and the relative change between the dollar signs. Basically, we see
      // which if the two $'s precedes the other, and we move that one forward until they line up.  in this case that
      // means:
      //
      //    0     10    20    30    40    50    60    70    80    90    100
      //    --------------------------------------------------------------------------------*----------------------
      //        |                                   /
      //        |                                /----
      //  T1      |                               /----
      //        |                            /----
      //        |                         /----
      //    ------------------------------------------------------------$------------------------------------------
      //        .          |              \
      //        .          |                 \
      //   T2       .          |                 \
      //        .          |                   \
      //        .          |                    \
      //    ----------------------------------------------------------------------*--------------------------------
      //
      // In other words (in this case), we're recognizing that the second edit happened after where the first edit
      // ended with a delta of 20 characters (60 - 40).  Thus, if we go back in time to where the first edit started
      // that's the same as if we started at char 80 instead of 60.
      //
      // As it so happens, the same logic applies if the second edit precedes the first edit.  In that case rather
      // than pushing the first edit forward to match the second, we'll push the second edit forward to match the
      // first.
      //
      // In this case that means we have { oldStart: 10, oldEnd: 80, newEnd: 70 } or, in TextChangeRange
      // semantics: { { start: 10, length: 70 }, newLength: 60 }
      //
      // The math then works out as follows.
      // If we have { oldStart1, oldEnd1, newEnd1 } and { oldStart2, oldEnd2, newEnd2 } then we can compute the
      // final result like so:
      //
      // {
      //    oldStart3: Min(oldStart1, oldStart2),
      //    oldEnd3  : Max(oldEnd1, oldEnd1 + (oldEnd2 - newEnd1)),
      //    newEnd3  : Max(newEnd2, newEnd2 + (newEnd1 - oldEnd2))
      // }

      val oldStart1 = oldStartN
      val oldEnd1 = oldEndN
      val newEnd1 = newEndN

      val oldStart2 = nextChange.span.start
      val oldEnd2 = textSpanEnd(nextChange.span)
      val newEnd2 = oldStart2 + nextChange.newLength

      oldStartN = Math.min(oldStart1, oldStart2)
      oldEndN = Math.max(oldEnd1, oldEnd1 + (oldEnd2 - newEnd1))
      newEndN = Math.max(newEnd2, newEnd2 + (newEnd1 - oldEnd2))
    }

    return createTextChangeRange(createTextSpanFromBounds(oldStartN, oldEndN), /*newLength:*/ newEndN - oldStartN)
  }

  def getTypeParameterOwner(d: Declaration): Declaration {
    if (d && d.kind == SyntaxKind.TypeParameter) {
      for (var current: Node = d; current; current = current.parent) {
        if (isFunctionLike(current) || isClassLike(current) || current.kind == SyntaxKind.InterfaceDeclaration) {
          return <Declaration>current
        }
      }
    }
  }

  def isParameterPropertyDeclaration(node: ParameterDeclaration): Boolean {
    return node.flags & NodeFlags.AccessibilityModifier && node.parent.kind == SyntaxKind.Constructor && isClassLike(node.parent.parent)
  }
}
