package be.doeraene.tsc

object Types {
  // branded String type used to store absolute, normalized and canonicalized paths
  // arbitrary file name can be converted to Path via toPath def
  final class Path(val path: String) extends AnyVal

  trait FileMap[T] {
    def get(fileName: Path): T
    def set(fileName: Path, value: T): Unit
    def contains(fileName: Path): Boolean
    def remove(fileName: Path): Unit

    def forEachValue(f: (Path, T) => Unit): Unit
    def clear(): Unit
  }

  trait TextRange {
    def pos: Int
    def end: Int
  }

  // token > SyntaxKind.Identifer => token is a keyword
  // Also, If you add a new SyntaxKind be sure to keep the `Markers` section at the bottom in sync
  object SyntaxKind extends Enumeration {
    val
    Unknown,
    EndOfFileToken,
    SingleLineCommentTrivia,
    MultiLineCommentTrivia,
    NewLineTrivia,
    WhitespaceTrivia,
    // We detect and preserve #! on the first line
    ShebangTrivia,
    // We detect and provide better error recovery when we encounter a git merge marker.  This
    // allows us to edit files with git-conflict markers in them in a much more pleasant manner.
    ConflictMarkerTrivia,
    // Literals
    NumericLiteral,
    StringLiteral,
    RegularExpressionLiteral,
    NoSubstitutionTemplateLiteral,
    // Pseudo-literals
    TemplateHead,
    TemplateMiddle,
    TemplateTail,
    // Punctuation
    OpenBraceToken,
    CloseBraceToken,
    OpenParenToken,
    CloseParenToken,
    OpenBracketToken,
    CloseBracketToken,
    DotToken,
    DotDotDotToken,
    SemicolonToken,
    CommaToken,
    LessThanToken,
    LessThanSlashToken,
    GreaterThanToken,
    LessThanEqualsToken,
    GreaterThanEqualsToken,
    EqualsEqualsToken,
    ExclamationEqualsToken,
    EqualsEqualsEqualsToken,
    ExclamationEqualsEqualsToken,
    EqualsGreaterThanToken,
    PlusToken,
    MinusToken,
    AsteriskToken,
    AsteriskAsteriskToken,
    SlashToken,
    PercentToken,
    PlusPlusToken,
    MinusMinusToken,
    LessThanLessThanToken,
    GreaterThanGreaterThanToken,
    GreaterThanGreaterThanGreaterThanToken,
    AmpersandToken,
    BarToken,
    CaretToken,
    ExclamationToken,
    TildeToken,
    AmpersandAmpersandToken,
    BarBarToken,
    QuestionToken,
    ColonToken,
    AtToken,
    // Assignments
    EqualsToken,
    PlusEqualsToken,
    MinusEqualsToken,
    AsteriskEqualsToken,
    AsteriskAsteriskEqualsToken,
    SlashEqualsToken,
    PercentEqualsToken,
    LessThanLessThanEqualsToken,
    GreaterThanGreaterThanEqualsToken,
    GreaterThanGreaterThanGreaterThanEqualsToken,
    AmpersandEqualsToken,
    BarEqualsToken,
    CaretEqualsToken,
    // Identifiers
    Identifier,
    // Reserved words
    BreakKeyword,
    CaseKeyword,
    CatchKeyword,
    ClassKeyword,
    ConstKeyword,
    ContinueKeyword,
    DebuggerKeyword,
    DefaultKeyword,
    DeleteKeyword,
    DoKeyword,
    ElseKeyword,
    EnumKeyword,
    ExportKeyword,
    ExtendsKeyword,
    FalseKeyword,
    FinallyKeyword,
    ForKeyword,
    FunctionKeyword,
    IfKeyword,
    ImportKeyword,
    InKeyword,
    InstanceOfKeyword,
    NewKeyword,
    NullKeyword,
    ReturnKeyword,
    SuperKeyword,
    SwitchKeyword,
    ThisKeyword,
    ThrowKeyword,
    TrueKeyword,
    TryKeyword,
    TypeOfKeyword,
    VarKeyword,
    VoidKeyword,
    WhileKeyword,
    WithKeyword,
    // Strict mode reserved words
    ImplementsKeyword,
    InterfaceKeyword,
    LetKeyword,
    PackageKeyword,
    PrivateKeyword,
    ProtectedKeyword,
    PublicKeyword,
    StaticKeyword,
    YieldKeyword,
    // Contextual keywords
    AbstractKeyword,
    AsKeyword,
    AnyKeyword,
    AsyncKeyword,
    AwaitKeyword,
    BooleanKeyword,
    ConstructorKeyword,
    DeclareKeyword,
    GetKeyword,
    IsKeyword,
    ModuleKeyword,
    NamespaceKeyword,
    ReadonlyKeyword,
    RequireKeyword,
    NumberKeyword,
    SetKeyword,
    StringKeyword,
    SymbolKeyword,
    TypeKeyword,
    FromKeyword,
    GlobalKeyword,
    OfKeyword, // LastKeyword and LastToken

    // Parse tree nodes

    // Names
    QualifiedName,
    ComputedPropertyName,
    // Signature elements
    TypeParameter,
    Parameter,
    Decorator,
    // TypeMember
    PropertySignature,
    PropertyDeclaration,
    MethodSignature,
    MethodDeclaration,
    Constructor,
    GetAccessor,
    SetAccessor,
    CallSignature,
    ConstructSignature,
    IndexSignature,
    // Type
    TypePredicate,
    TypeReference,
    FunctionType,
    ConstructorType,
    TypeQuery,
    TypeLiteral,
    ArrayType,
    TupleType,
    UnionType,
    IntersectionType,
    ParenthesizedType,
    ThisType,
    StringLiteralType,
    // Binding patterns
    ObjectBindingPattern,
    ArrayBindingPattern,
    BindingElement,
    // Expression
    ArrayLiteralExpression,
    ObjectLiteralExpression,
    PropertyAccessExpression,
    ElementAccessExpression,
    CallExpression,
    NewExpression,
    TaggedTemplateExpression,
    TypeAssertionExpression,
    ParenthesizedExpression,
    FunctionExpression,
    ArrowFunction,
    DeleteExpression,
    TypeOfExpression,
    VoidExpression,
    AwaitExpression,
    PrefixUnaryExpression,
    PostfixUnaryExpression,
    BinaryExpression,
    ConditionalExpression,
    TemplateExpression,
    YieldExpression,
    SpreadElementExpression,
    ClassExpression,
    OmittedExpression,
    ExpressionWithTypeArguments,
    AsExpression,

    // Misc
    TemplateSpan,
    SemicolonClassElement,
    // Element
    Block,
    VariableStatement,
    EmptyStatement,
    ExpressionStatement,
    IfStatement,
    DoStatement,
    WhileStatement,
    ForStatement,
    ForInStatement,
    ForOfStatement,
    ContinueStatement,
    BreakStatement,
    ReturnStatement,
    WithStatement,
    SwitchStatement,
    LabeledStatement,
    ThrowStatement,
    TryStatement,
    DebuggerStatement,
    VariableDeclaration,
    VariableDeclarationList,
    FunctionDeclaration,
    ClassDeclaration,
    InterfaceDeclaration,
    TypeAliasDeclaration,
    EnumDeclaration,
    ModuleDeclaration,
    ModuleBlock,
    CaseBlock,
    ImportEqualsDeclaration,
    ImportDeclaration,
    ImportClause,
    NamespaceImport,
    NamedImports,
    ImportSpecifier,
    ExportAssignment,
    ExportDeclaration,
    NamedExports,
    ExportSpecifier,
    MissingDeclaration,

    // Module references
    ExternalModuleReference,

    // JSX
    JsxElement,
    JsxSelfClosingElement,
    JsxOpeningElement,
    JsxText,
    JsxClosingElement,
    JsxAttribute,
    JsxSpreadAttribute,
    JsxExpression,

    // Clauses
    CaseClause,
    DefaultClause,
    HeritageClause,
    CatchClause,

    // Property assignments
    PropertyAssignment,
    ShorthandPropertyAssignment,

    // Enum
    EnumMember,
    // Top-level nodes
    SourceFile,

    // JSDoc nodes
    JSDocTypeExpression,
    // The * type
    JSDocAllType,
    // The ? type
    JSDocUnknownType,
    JSDocArrayType,
    JSDocUnionType,
    JSDocTupleType,
    JSDocNullableType,
    JSDocNonNullableType,
    JSDocRecordType,
    JSDocRecordMember,
    JSDocTypeReference,
    JSDocOptionalType,
    JSDocFunctionType,
    JSDocVariadicType,
    JSDocConstructorType,
    JSDocThisType,
    JSDocComment,
    JSDocTag,
    JSDocParameterTag,
    JSDocReturnTag,
    JSDocTypeTag,
    JSDocTemplateTag,

    // Synthesized list
    SyntaxList,
    // Enum value count
    Count = Value

    // Markers
    val FirstAssignment = EqualsToken
    val LastAssignment = CaretEqualsToken
    val FirstReservedWord = BreakKeyword
    val LastReservedWord = WithKeyword
    val FirstKeyword = BreakKeyword
    val LastKeyword = OfKeyword
    val FirstFutureReservedWord = ImplementsKeyword
    val LastFutureReservedWord = YieldKeyword
    val FirstTypeNode = TypePredicate
    val LastTypeNode = StringLiteralType
    val FirstPunctuation = OpenBraceToken
    val LastPunctuation = CaretEqualsToken
    val FirstToken = Unknown
    val LastToken = LastKeyword
    val FirstTriviaToken = SingleLineCommentTrivia
    val LastTriviaToken = ConflictMarkerTrivia
    val FirstLiteralToken = NumericLiteral
    val LastLiteralToken = NoSubstitutionTemplateLiteral
    val FirstTemplateToken = NoSubstitutionTemplateLiteral
    val LastTemplateToken = TemplateTail
    val FirstBinaryOperator = LessThanToken
    val LastBinaryOperator = CaretEqualsToken
    val FirstNode = QualifiedName
  }

  object NodeFlags {
    final val None =         0
    final val Export =       1 << 0  // Declarations
    final val Ambient =      1 << 1  // Declarations
    final val Public =       1 << 2  // Property/Method
    final val Private =      1 << 3  // Property/Method
    final val Protected =      1 << 4  // Property/Method
    final val Static =       1 << 5  // Property/Method
    final val Readonly =       1 << 6  // Property/Method
    final val Abstract =       1 << 7  // Class/Method/ConstructSignature
    final val Async =        1 << 8  // Property/Method/Function
    final val Default =      1 << 9  // Function/Class (export default declaration)
    final val Let =        1 << 10  // Variable declaration
    final val Const =        1 << 11  // Variable declaration
    final val Namespace =      1 << 12  // Namespace declaration
    final val ExportContext =    1 << 13  // Export context (initialized by binding)
    final val ContainsThis =     1 << 14  // Interface contains references to "this"
    final val HasImplicitReturn =  1 << 15  // If def implicitly returns on one of codepaths (initialized by binding)
    final val HasExplicitReturn =  1 << 16  // If def has explicit reachable return on one of codepaths (initialized by binding)
    final val GlobalAugmentation = 1 << 17  // Set if module declaration is an augmentation for the global scope
    final val HasClassExtends =  1 << 18  // If the file has a non-ambient class with an extends clause in ES5 or lower (initialized by binding)
    final val HasDecorators =    1 << 19  // If the file has decorators (initialized by binding)
    final val HasParamDecorators = 1 << 20  // If the file has parameter decorators (initialized by binding)
    final val HasAsyncFunctions =  1 << 21  // If the file has async functions (initialized by binding)
    final val DisallowInContext =  1 << 22  // If node was parsed in a context where 'in-expressions' are not allowed
    final val YieldContext =     1 << 23  // If node was parsed in the 'yield' context created when parsing a generator
    final val DecoratorContext =   1 << 24  // If node was parsed as part of a decorator
    final val AwaitContext =     1 << 25  // If node was parsed in the 'await' context created when parsing an async def
    final val ThisNodeHasError =   1 << 26  // If the parser encountered an error when parsing the code that created this node
    final val JavaScriptFile =   1 << 27  // If node was parsed in a JavaScript
    final val ThisNodeOrAnySubNodesHasError = 1 << 28  // If this node or any of its children had an error
    final val HasAggregatedChildData = 1 << 29  // If we've computed data from children and cached it in this node

    final val Modifier = Export | Ambient | Public | Private | Protected | Static | Abstract | Default | Async
    final val AccessibilityModifier = Public | Private | Protected
    final val BlockScoped = Let | Const

    final val ReachabilityCheckFlags = HasImplicitReturn | HasExplicitReturn
    final val EmitHelperFlags = HasClassExtends | HasDecorators | HasParamDecorators | HasAsyncFunctions

    // Parsing context flags
    final val ContextFlags = DisallowInContext | YieldContext | DecoratorContext | AwaitContext

    // Exclude these flags when parsing a Type
    final val TypeExcludesFlags = YieldContext | AwaitContext
  }

  object JsxFlags {
    final val None = 0
    /** An element from a named property of the JSX.IntrinsicElements trait */
    final val IntrinsicNamedElement = 1 << 0
    /** An element inferred from the String index signature of the JSX.IntrinsicElements trait */
    final val IntrinsicIndexedElement = 1 << 1

    final val IntrinsicElement = IntrinsicNamedElement | IntrinsicIndexedElement
  }


  /* @internal */
  object RelationComparisonResult {
    final val Succeeded = 1 // Should be truthy
    final val Failed = 2
    final val FailedAndReported = 3
  }

  trait Node extends TextRange {
    kind: SyntaxKind
    flags: NodeFlags
    decorators?: NodeArray<Decorator>;        // Array of decorators (in document order)
    modifiers?: ModifiersArray;           // Array of modifiers
    /* @internal */ id?: Int;          // Unique id (used to look up NodeLinks)
    parent?: Node;                  // Parent node (initialized by binding
    /* @internal */ jsDocComment?: JSDocComment;  // JSDoc for the node, if it has any.  Only for .js files.
    /* @internal */ symbol?: Symbol;        // Symbol declared by node (initialized by binding)
    /* @internal */ locals?: SymbolTable;       // Locals associated with node (initialized by binding)
    /* @internal */ nextContainer?: Node;       // Next container in declaration order (initialized by binding)
    /* @internal */ localSymbol?: Symbol;       // Local symbol declared by node (initialized by binding only for exported nodes)
  }

  trait NodeArray<T> extends Array<T>, TextRange {
    hasTrailingComma?: Boolean
  }

  trait ModifiersArray extends NodeArray<Modifier> {
    flags: Int
  }

  // @kind(SyntaxKind.AbstractKeyword)
  // @kind(SyntaxKind.AsyncKeyword)
  // @kind(SyntaxKind.ConstKeyword)
  // @kind(SyntaxKind.DeclareKeyword)
  // @kind(SyntaxKind.DefaultKeyword)
  // @kind(SyntaxKind.ExportKeyword)
  // @kind(SyntaxKind.PublicKeyword)
  // @kind(SyntaxKind.PrivateKeyword)
  // @kind(SyntaxKind.ProtectedKeyword)
  // @kind(SyntaxKind.StaticKeyword)
  trait Modifier extends Node { }

  // @kind(SyntaxKind.Identifier)
  trait Identifier extends PrimaryExpression {
    text: String;                  // Text of identifier (with escapes converted to characters)
    originalKeywordKind?: SyntaxKind;        // Original syntaxKind which get set so that we can report an error later
  }

  // @kind(SyntaxKind.QualifiedName)
  trait QualifiedName extends Node {
    // Must have same layout as PropertyAccess
    left: EntityName
    right: Identifier
  }

  type EntityName = Identifier | QualifiedName

  type PropertyName = Identifier | LiteralExpression | ComputedPropertyName

  type DeclarationName = Identifier | LiteralExpression | ComputedPropertyName | BindingPattern

  trait Declaration extends Node {
    _declarationBrand: any
    name?: DeclarationName
  }

  trait DeclarationStatement extends Declaration, Statement {
    name?: Identifier
  }

  // @kind(SyntaxKind.ComputedPropertyName)
  trait ComputedPropertyName extends Node {
    expression: Expression
  }

  // @kind(SyntaxKind.Decorator)
  trait Decorator extends Node {
    expression: LeftHandSideExpression
  }

  // @kind(SyntaxKind.TypeParameter)
  trait TypeParameterDeclaration extends Declaration {
    name: Identifier
    constraint?: TypeNode

    // For error recovery purposes.
    expression?: Expression
  }

  trait SignatureDeclaration extends Declaration {
    name?: PropertyName
    typeParameters?: NodeArray<TypeParameterDeclaration>
    parameters: NodeArray<ParameterDeclaration>
    type?: TypeNode
  }

  // @kind(SyntaxKind.CallSignature)
  trait CallSignatureDeclaration extends SignatureDeclaration, TypeElement { }

  // @kind(SyntaxKind.ConstructSignature)
  trait ConstructSignatureDeclaration extends SignatureDeclaration, TypeElement { }

  // @kind(SyntaxKind.VariableDeclaration)
  trait VariableDeclaration extends Declaration {
    parent?: VariableDeclarationList
    name: Identifier | BindingPattern;  // Declared variable name
    type?: TypeNode;          // Optional type annotation
    initializer?: Expression;       // Optional initializer
  }

  // @kind(SyntaxKind.VariableDeclarationList)
  trait VariableDeclarationList extends Node {
    declarations: NodeArray<VariableDeclaration>
  }

  // @kind(SyntaxKind.Parameter)
  trait ParameterDeclaration extends Declaration {
    dotDotDotToken?: Node;        // Present on rest parameter
    name: Identifier | BindingPattern;  // Declared parameter name
    questionToken?: Node;         // Present on optional parameter
    type?: TypeNode;          // Optional type annotation
    initializer?: Expression;       // Optional initializer
  }

  // @kind(SyntaxKind.BindingElement)
  trait BindingElement extends Declaration {
    propertyName?: PropertyName;    // Binding property name (in object binding pattern)
    dotDotDotToken?: Node;        // Present on rest binding element
    name: Identifier | BindingPattern;  // Declared binding element name
    initializer?: Expression;       // Optional initializer
  }

  // @kind(SyntaxKind.PropertySignature)
  trait PropertySignature extends TypeElement {
    name: PropertyName;         // Declared property name
    questionToken?: Node;         // Present on optional property
    type?: TypeNode;          // Optional type annotation
    initializer?: Expression;       // Optional initializer
  }

  // @kind(SyntaxKind.PropertyDeclaration)
  trait PropertyDeclaration extends ClassElement {
    questionToken?: Node;         // Present for use with reporting a grammar error
    name: PropertyName
    type?: TypeNode
    initializer?: Expression;       // Optional initializer
  }

  trait ObjectLiteralElement extends Declaration {
    _objectLiteralBrandBrand: any
    name?: PropertyName
   }

  // @kind(SyntaxKind.PropertyAssignment)
  trait PropertyAssignment extends ObjectLiteralElement {
    _propertyAssignmentBrand: any
    name: PropertyName
    questionToken?: Node
    initializer: Expression
  }

  // @kind(SyntaxKind.ShorthandPropertyAssignment)
  trait ShorthandPropertyAssignment extends ObjectLiteralElement {
    name: Identifier
    questionToken?: Node
    // used when ObjectLiteralExpression is used in ObjectAssignmentPattern
    // it is grammar error to appear in actual object initializer
    equalsToken?: Node
    objectAssignmentInitializer?: Expression
  }

  // SyntaxKind.VariableDeclaration
  // SyntaxKind.Parameter
  // SyntaxKind.BindingElement
  // SyntaxKind.Property
  // SyntaxKind.PropertyAssignment
  // SyntaxKind.ShorthandPropertyAssignment
  // SyntaxKind.EnumMember
  trait VariableLikeDeclaration extends Declaration {
    propertyName?: PropertyName
    dotDotDotToken?: Node
    name: DeclarationName
    questionToken?: Node
    type?: TypeNode
    initializer?: Expression
  }

  trait PropertyLikeDeclaration extends Declaration {
    name: PropertyName
  }

  trait BindingPattern extends Node {
    elements: NodeArray<BindingElement>
  }

  // @kind(SyntaxKind.ObjectBindingPattern)
  trait ObjectBindingPattern extends BindingPattern { }

  // @kind(SyntaxKind.ArrayBindingPattern)
  trait ArrayBindingPattern extends BindingPattern { }

  /**
   * Several node kinds share def-like features such as a signature,
   * a name, and a body. These nodes should extend FunctionLikeDeclaration.
   * Examples:
   * - FunctionDeclaration
   * - MethodDeclaration
   * - AccessorDeclaration
   */
  trait FunctionLikeDeclaration extends SignatureDeclaration {
    _functionLikeDeclarationBrand: any

    asteriskToken?: Node
    questionToken?: Node
    body?: Block | Expression
  }

  // @kind(SyntaxKind.FunctionDeclaration)
  trait FunctionDeclaration extends FunctionLikeDeclaration, DeclarationStatement {
    name?: Identifier
    body?: FunctionBody
  }

  // @kind(SyntaxKind.MethodSignature)
  trait MethodSignature extends SignatureDeclaration, TypeElement {
    name: PropertyName
  }

  // Note that a MethodDeclaration is considered both a ClassElement and an ObjectLiteralElement.
  // Both the grammars for ClassDeclaration and ObjectLiteralExpression allow for MethodDeclarations
  // as child elements, and so a MethodDeclaration satisfies both interfaces.  This avoids the
  // alternative where we would need separate kinds/types for ClassMethodDeclaration and
  // ObjectLiteralMethodDeclaration, which would look identical.
  //
  // Because of this, it may be necessary to determine what sort of MethodDeclaration you have
  // at later stages of the compiler pipeline.  In that case, you can either check the parent kind
  // of the method, or use helpers like isObjectLiteralMethodDeclaration
  // @kind(SyntaxKind.MethodDeclaration)
  trait MethodDeclaration extends FunctionLikeDeclaration, ClassElement, ObjectLiteralElement {
    name: PropertyName
    body?: FunctionBody
  }

  // @kind(SyntaxKind.Constructor)
  trait ConstructorDeclaration extends FunctionLikeDeclaration, ClassElement {
    body?: FunctionBody
  }

  // For when we encounter a semicolon in a class declaration.  ES6 allows these as class elements.
  // @kind(SyntaxKind.SemicolonClassElement)
  trait SemicolonClassElement extends ClassElement {
    _semicolonClassElementBrand: any
  }

  // See the comment on MethodDeclaration for the intuition behind AccessorDeclaration being a
  // ClassElement and an ObjectLiteralElement.
  trait AccessorDeclaration extends FunctionLikeDeclaration, ClassElement, ObjectLiteralElement {
    _accessorDeclarationBrand: any
    name: PropertyName
    body: FunctionBody
  }

  // @kind(SyntaxKind.GetAccessor)
  trait GetAccessorDeclaration extends AccessorDeclaration { }

  // @kind(SyntaxKind.SetAccessor)
  trait SetAccessorDeclaration extends AccessorDeclaration { }

  // @kind(SyntaxKind.IndexSignature)
  trait IndexSignatureDeclaration extends SignatureDeclaration, ClassElement, TypeElement {
    _indexSignatureDeclarationBrand: any
  }

  // @kind(SyntaxKind.AnyKeyword)
  // @kind(SyntaxKind.NumberKeyword)
  // @kind(SyntaxKind.BooleanKeyword)
  // @kind(SyntaxKind.StringKeyword)
  // @kind(SyntaxKind.SymbolKeyword)
  // @kind(SyntaxKind.VoidKeyword)
  trait TypeNode extends Node {
    _typeNodeBrand: any
  }

  // @kind(SyntaxKind.ThisType)
  trait ThisTypeNode extends TypeNode {
    _thisTypeNodeBrand: any
  }

  trait FunctionOrConstructorTypeNode extends TypeNode, SignatureDeclaration {
    _functionOrConstructorTypeNodeBrand: any
  }

  // @kind(SyntaxKind.FunctionType)
  trait FunctionTypeNode extends FunctionOrConstructorTypeNode { }

  // @kind(SyntaxKind.ConstructorType)
  trait ConstructorTypeNode extends FunctionOrConstructorTypeNode { }

  // @kind(SyntaxKind.TypeReference)
  trait TypeReferenceNode extends TypeNode {
    typeName: EntityName
    typeArguments?: NodeArray<TypeNode>
  }

  // @kind(SyntaxKind.TypePredicate)
  trait TypePredicateNode extends TypeNode {
    parameterName: Identifier | ThisTypeNode
    type: TypeNode
  }

  // @kind(SyntaxKind.TypeQuery)
  trait TypeQueryNode extends TypeNode {
    exprName: EntityName
  }

  // A TypeLiteral is the declaration node for an anonymous symbol.
  // @kind(SyntaxKind.TypeLiteral)
  trait TypeLiteralNode extends TypeNode, Declaration {
    members: NodeArray<TypeElement>
  }

  // @kind(SyntaxKind.ArrayType)
  trait ArrayTypeNode extends TypeNode {
    elementType: TypeNode
  }

  // @kind(SyntaxKind.TupleType)
  trait TupleTypeNode extends TypeNode {
    elementTypes: NodeArray<TypeNode>
  }

  trait UnionOrIntersectionTypeNode extends TypeNode {
    types: NodeArray<TypeNode>
  }

  // @kind(SyntaxKind.UnionType)
  trait UnionTypeNode extends UnionOrIntersectionTypeNode { }

  // @kind(SyntaxKind.IntersectionType)
  trait IntersectionTypeNode extends UnionOrIntersectionTypeNode { }

  // @kind(SyntaxKind.ParenthesizedType)
  trait ParenthesizedTypeNode extends TypeNode {
    type: TypeNode
  }

  // @kind(SyntaxKind.StringLiteralType)
  trait StringLiteralTypeNode extends LiteralLikeNode, TypeNode {
    _stringLiteralTypeBrand: any
  }

  // @kind(SyntaxKind.StringLiteral)
  trait StringLiteral extends LiteralExpression {
    _stringLiteralBrand: any
  }

  // Note: 'brands' in our syntax nodes serve to give us a small amount of nominal typing.
  // Consider 'Expression'.  Without the brand, 'Expression' is actually no different
  // (structurally) than 'Node'.  Because of this you can pass any Node to a def that
  // takes an Expression without any error.  By using the 'brands' we ensure that the type
  // checker actually thinks you have something of the right type.  Note: the brands are
  // never actually given values.  At runtime they have zero cost.

  trait Expression extends Node {
    _expressionBrand: any
    contextualType?: Type;  // Used to temporarily assign a contextual type during overload resolution
  }

  // @kind(SyntaxKind.OmittedExpression)
  trait OmittedExpression extends Expression { }

  trait UnaryExpression extends Expression {
    _unaryExpressionBrand: any
  }

  trait IncrementExpression extends UnaryExpression {
    _incrementExpressionBrand: any
  }

  // @kind(SyntaxKind.PrefixUnaryExpression)
  trait PrefixUnaryExpression extends IncrementExpression {
    operator: SyntaxKind
    operand: UnaryExpression
  }

  // @kind(SyntaxKind.PostfixUnaryExpression)
  trait PostfixUnaryExpression extends IncrementExpression {
    operand: LeftHandSideExpression
    operator: SyntaxKind
  }

  trait PostfixExpression extends UnaryExpression {
    _postfixExpressionBrand: any
  }

  trait LeftHandSideExpression extends IncrementExpression {
    _leftHandSideExpressionBrand: any
  }

  trait MemberExpression extends LeftHandSideExpression {
    _memberExpressionBrand: any
  }

  // @kind(SyntaxKind.TrueKeyword)
  // @kind(SyntaxKind.FalseKeyword)
  // @kind(SyntaxKind.NullKeyword)
  // @kind(SyntaxKind.ThisKeyword)
  // @kind(SyntaxKind.SuperKeyword)
  trait PrimaryExpression extends MemberExpression {
    _primaryExpressionBrand: any
  }

  // @kind(SyntaxKind.DeleteExpression)
  trait DeleteExpression extends UnaryExpression {
    expression: UnaryExpression
  }

  // @kind(SyntaxKind.TypeOfExpression)
  trait TypeOfExpression extends UnaryExpression {
    expression: UnaryExpression
  }

  // @kind(SyntaxKind.VoidExpression)
  trait VoidExpression extends UnaryExpression {
    expression: UnaryExpression
  }

  // @kind(SyntaxKind.AwaitExpression)
  trait AwaitExpression extends UnaryExpression {
    expression: UnaryExpression
  }

  // @kind(SyntaxKind.YieldExpression)
  trait YieldExpression extends Expression {
    asteriskToken?: Node
    expression?: Expression
  }

  // @kind(SyntaxKind.BinaryExpression)
  // Binary expressions can be declarations if they are 'exports.foo = bar' expressions in JS files
  trait BinaryExpression extends Expression, Declaration {
    left: Expression
    operatorToken: Node
    right: Expression
  }

  // @kind(SyntaxKind.ConditionalExpression)
  trait ConditionalExpression extends Expression {
    condition: Expression
    questionToken: Node
    whenTrue: Expression
    colonToken: Node
    whenFalse: Expression
  }

  type FunctionBody = Block
  type ConciseBody = FunctionBody | Expression

  // @kind(SyntaxKind.FunctionExpression)
  trait FunctionExpression extends PrimaryExpression, FunctionLikeDeclaration {
    name?: Identifier
    body: FunctionBody;  // Required, whereas the member inherited from FunctionDeclaration is optional
  }

  // @kind(SyntaxKind.ArrowFunction)
  trait ArrowFunction extends Expression, FunctionLikeDeclaration {
    equalsGreaterThanToken: Node
    body: ConciseBody
  }

  trait LiteralLikeNode extends Node {
    text: String
    isUnterminated?: Boolean
    hasExtendedUnicodeEscape?: Boolean
    /* @internal */
    isOctalLiteral?: Boolean
  }

  // The text property of a LiteralExpression stores the interpreted value of the literal in text form. For a StringLiteral,
  // or any literal of a template, this means quotes have been removed and escapes have been converted to actual characters.
  // For a NumericLiteral, the stored value is the toString() representation of the Int. For example 1, 1.00, and 1e0 are all stored as just "1".
  // @kind(SyntaxKind.NumericLiteral)
  // @kind(SyntaxKind.RegularExpressionLiteral)
  // @kind(SyntaxKind.NoSubstitutionTemplateLiteral)
  trait LiteralExpression extends LiteralLikeNode, PrimaryExpression {
    _literalExpressionBrand: any
  }

  // @kind(SyntaxKind.TemplateHead)
  // @kind(SyntaxKind.TemplateMiddle)
  // @kind(SyntaxKind.TemplateTail)
  trait TemplateLiteralFragment extends LiteralLikeNode {
    _templateLiteralFragmentBrand: any
  }

  // @kind(SyntaxKind.TemplateExpression)
  trait TemplateExpression extends PrimaryExpression {
    head: TemplateLiteralFragment
    templateSpans: NodeArray<TemplateSpan>
  }

  // Each of these corresponds to a substitution expression and a template literal, in that order.
  // The template literal must have kind TemplateMiddleLiteral or TemplateTailLiteral.
  // @kind(SyntaxKind.TemplateSpan)
  trait TemplateSpan extends Node {
    expression: Expression
    literal: TemplateLiteralFragment
  }

  // @kind(SyntaxKind.ParenthesizedExpression)
  trait ParenthesizedExpression extends PrimaryExpression {
    expression: Expression
  }

  // @kind(SyntaxKind.ArrayLiteralExpression)
  trait ArrayLiteralExpression extends PrimaryExpression {
    elements: NodeArray<Expression>
    /* @internal */
    multiLine?: Boolean
  }

  // @kind(SyntaxKind.SpreadElementExpression)
  trait SpreadElementExpression extends Expression {
    expression: Expression
  }

  // An ObjectLiteralExpression is the declaration node for an anonymous symbol.
  // @kind(SyntaxKind.ObjectLiteralExpression)
  trait ObjectLiteralExpression extends PrimaryExpression, Declaration {
    properties: NodeArray<ObjectLiteralElement>
    /* @internal */
    multiLine?: Boolean
  }

  // @kind(SyntaxKind.PropertyAccessExpression)
  trait PropertyAccessExpression extends MemberExpression, Declaration {
    expression: LeftHandSideExpression
    dotToken: Node
    name: Identifier
  }

  // @kind(SyntaxKind.ElementAccessExpression)
  trait ElementAccessExpression extends MemberExpression {
    expression: LeftHandSideExpression
    argumentExpression?: Expression
  }

  // @kind(SyntaxKind.CallExpression)
  trait CallExpression extends LeftHandSideExpression, Declaration {
    expression: LeftHandSideExpression
    typeArguments?: NodeArray<TypeNode>
    arguments: NodeArray<Expression>
  }

  // @kind(SyntaxKind.ExpressionWithTypeArguments)
  trait ExpressionWithTypeArguments extends TypeNode {
    expression: LeftHandSideExpression
    typeArguments?: NodeArray<TypeNode>
  }

  // @kind(SyntaxKind.NewExpression)
  trait NewExpression extends CallExpression, PrimaryExpression { }

  // @kind(SyntaxKind.TaggedTemplateExpression)
  trait TaggedTemplateExpression extends MemberExpression {
    tag: LeftHandSideExpression
    template: LiteralExpression | TemplateExpression
  }

  type CallLikeExpression = CallExpression | NewExpression | TaggedTemplateExpression | Decorator

  // @kind(SyntaxKind.AsExpression)
  trait AsExpression extends Expression {
    expression: Expression
    type: TypeNode
  }

  // @kind(SyntaxKind.TypeAssertionExpression)
  trait TypeAssertion extends UnaryExpression {
    type: TypeNode
    expression: UnaryExpression
  }

  type AssertionExpression = TypeAssertion | AsExpression

  /// A JSX expression of the form <TagName attrs>...</TagName>
  // @kind(SyntaxKind.JsxElement)
  trait JsxElement extends PrimaryExpression {
    openingElement: JsxOpeningElement
    children: NodeArray<JsxChild>
    closingElement: JsxClosingElement
  }

  /// The opening element of a <Tag>...</Tag> JsxElement
  // @kind(SyntaxKind.JsxOpeningElement)
  trait JsxOpeningElement extends Expression {
    _openingElementBrand?: any
    tagName: EntityName
    attributes: NodeArray<JsxAttribute | JsxSpreadAttribute>
  }

  /// A JSX expression of the form <TagName attrs />
  // @kind(SyntaxKind.JsxSelfClosingElement)
  trait JsxSelfClosingElement extends PrimaryExpression, JsxOpeningElement {
    _selfClosingElementBrand?: any
  }

  /// Either the opening tag in a <Tag>...</Tag> pair, or the lone <Tag /> in a self-closing form
  type JsxOpeningLikeElement = JsxSelfClosingElement | JsxOpeningElement

  // @kind(SyntaxKind.JsxAttribute)
  trait JsxAttribute extends Node {
    name: Identifier
    /// JSX attribute initializers are optional; <X y /> is sugar for <X y={true} />
    initializer?: Expression
  }

  // @kind(SyntaxKind.JsxSpreadAttribute)
  trait JsxSpreadAttribute extends Node {
    expression: Expression
  }

  // @kind(SyntaxKind.JsxClosingElement)
  trait JsxClosingElement extends Node {
    tagName: EntityName
  }

  // @kind(SyntaxKind.JsxExpression)
  trait JsxExpression extends Expression {
    expression?: Expression
  }

  // @kind(SyntaxKind.JsxText)
  trait JsxText extends Node {
    _jsxTextExpressionBrand: any
  }

  type JsxChild = JsxText | JsxExpression | JsxElement | JsxSelfClosingElement

  trait Statement extends Node {
    _statementBrand: any
  }

  // @kind(SyntaxKind.EmptyStatement)
  trait EmptyStatement extends Statement { }

  // @kind(SyntaxKind.DebuggerStatement)
  trait DebuggerStatement extends Statement { }

  // @kind(SyntaxKind.MissingDeclaration)
  trait MissingDeclaration extends DeclarationStatement, ClassElement, ObjectLiteralElement, TypeElement {
    name?: Identifier
  }

  type BlockLike = SourceFile | Block | ModuleBlock | CaseClause

  // @kind(SyntaxKind.Block)
  trait Block extends Statement {
    statements: NodeArray<Statement>
  }

  // @kind(SyntaxKind.VariableStatement)
  trait VariableStatement extends Statement {
    declarationList: VariableDeclarationList
  }

  // @kind(SyntaxKind.ExpressionStatement)
  trait ExpressionStatement extends Statement {
    expression: Expression
  }

  // @kind(SyntaxKind.IfStatement)
  trait IfStatement extends Statement {
    expression: Expression
    thenStatement: Statement
    elseStatement?: Statement
  }

  trait IterationStatement extends Statement {
    statement: Statement
  }

  // @kind(SyntaxKind.DoStatement)
  trait DoStatement extends IterationStatement {
    expression: Expression
  }

  // @kind(SyntaxKind.WhileStatement)
  trait WhileStatement extends IterationStatement {
    expression: Expression
  }

  // @kind(SyntaxKind.ForStatement)
  trait ForStatement extends IterationStatement {
    initializer?: VariableDeclarationList | Expression
    condition?: Expression
    incrementor?: Expression
  }

  // @kind(SyntaxKind.ForInStatement)
  trait ForInStatement extends IterationStatement {
    initializer: VariableDeclarationList | Expression
    expression: Expression
  }

  // @kind(SyntaxKind.ForOfStatement)
  trait ForOfStatement extends IterationStatement {
    initializer: VariableDeclarationList | Expression
    expression: Expression
  }

  // @kind(SyntaxKind.BreakStatement)
  trait BreakStatement extends Statement {
    label?: Identifier
  }

  // @kind(SyntaxKind.ContinueStatement)
  trait ContinueStatement extends Statement {
    label?: Identifier
  }

  type BreakOrContinueStatement = BreakStatement | ContinueStatement

  // @kind(SyntaxKind.ReturnStatement)
  trait ReturnStatement extends Statement {
    expression?: Expression
  }

  // @kind(SyntaxKind.WithStatement)
  trait WithStatement extends Statement {
    expression: Expression
    statement: Statement
  }

  // @kind(SyntaxKind.SwitchStatement)
  trait SwitchStatement extends Statement {
    expression: Expression
    caseBlock: CaseBlock
  }

  // @kind(SyntaxKind.CaseBlock)
  trait CaseBlock extends Node {
    clauses: NodeArray<CaseOrDefaultClause>
  }

  // @kind(SyntaxKind.CaseClause)
  trait CaseClause extends Node {
    expression: Expression
    statements: NodeArray<Statement>
  }

  // @kind(SyntaxKind.DefaultClause)
  trait DefaultClause extends Node {
    statements: NodeArray<Statement>
  }

  type CaseOrDefaultClause = CaseClause | DefaultClause

  // @kind(SyntaxKind.LabeledStatement)
  trait LabeledStatement extends Statement {
    label: Identifier
    statement: Statement
  }

  // @kind(SyntaxKind.ThrowStatement)
  trait ThrowStatement extends Statement {
    expression: Expression
  }

  // @kind(SyntaxKind.TryStatement)
  trait TryStatement extends Statement {
    tryBlock: Block
    catchClause?: CatchClause
    finallyBlock?: Block
  }

  // @kind(SyntaxKind.CatchClause)
  trait CatchClause extends Node {
    variableDeclaration: VariableDeclaration
    block: Block
  }

  trait ClassLikeDeclaration extends Declaration {
    name?: Identifier
    typeParameters?: NodeArray<TypeParameterDeclaration>
    heritageClauses?: NodeArray<HeritageClause>
    members: NodeArray<ClassElement>
  }

  // @kind(SyntaxKind.ClassDeclaration)
  trait ClassDeclaration extends ClassLikeDeclaration, DeclarationStatement {
    name?: Identifier
  }

  // @kind(SyntaxKind.ClassExpression)
  trait ClassExpression extends ClassLikeDeclaration, PrimaryExpression {
  }

  trait ClassElement extends Declaration {
    _classElementBrand: any
    name?: PropertyName
  }

  trait TypeElement extends Declaration {
    _typeElementBrand: any
    name?: PropertyName
    questionToken?: Node
  }

  // @kind(SyntaxKind.InterfaceDeclaration)
  trait InterfaceDeclaration extends DeclarationStatement {
    name: Identifier
    typeParameters?: NodeArray<TypeParameterDeclaration>
    heritageClauses?: NodeArray<HeritageClause>
    members: NodeArray<TypeElement>
  }

  // @kind(SyntaxKind.HeritageClause)
  trait HeritageClause extends Node {
    token: SyntaxKind
    types?: NodeArray<ExpressionWithTypeArguments>
  }

  // @kind(SyntaxKind.TypeAliasDeclaration)
  trait TypeAliasDeclaration extends DeclarationStatement {
    name: Identifier
    typeParameters?: NodeArray<TypeParameterDeclaration>
    type: TypeNode
  }

  // @kind(SyntaxKind.EnumMember)
  trait EnumMember extends Declaration {
    // This does include ComputedPropertyName, but the parser will give an error
    // if it parses a ComputedPropertyName in an EnumMember
    name: DeclarationName
    initializer?: Expression
  }

  // @kind(SyntaxKind.EnumDeclaration)
  trait EnumDeclaration extends DeclarationStatement {
    name: Identifier
    members: NodeArray<EnumMember>
  }

  type ModuleBody = ModuleBlock | ModuleDeclaration

  // @kind(SyntaxKind.ModuleDeclaration)
  trait ModuleDeclaration extends DeclarationStatement {
    name: Identifier | LiteralExpression
    body: ModuleBlock | ModuleDeclaration
  }

  // @kind(SyntaxKind.ModuleBlock)
  trait ModuleBlock extends Node, Statement {
    statements: NodeArray<Statement>
  }

  // @kind(SyntaxKind.ImportEqualsDeclaration)
  trait ImportEqualsDeclaration extends DeclarationStatement {
    name: Identifier

    // 'EntityName' for an internal module reference, 'ExternalModuleReference' for an external
    // module reference.
    moduleReference: EntityName | ExternalModuleReference
  }

  // @kind(SyntaxKind.ExternalModuleReference)
  trait ExternalModuleReference extends Node {
    expression?: Expression
  }

  // In case of:
  // import "mod"  => importClause = (), moduleSpecifier = "mod"
  // In rest of the cases, module specifier is String literal corresponding to module
  // ImportClause information is shown at its declaration below.
  // @kind(SyntaxKind.ImportDeclaration)
  trait ImportDeclaration extends Statement {
    importClause?: ImportClause
    moduleSpecifier: Expression
  }

  // In case of:
  // import d from "mod" => name = d, namedBinding = ()
  // import * as ns from "mod" => name = (), namedBinding: NamespaceImport = { name: ns }
  // import d, * as ns from "mod" => name = d, namedBinding: NamespaceImport = { name: ns }
  // import { a, b as x } from "mod" => name = (), namedBinding: NamedImports = { elements: [{ name: a }, { name: x, propertyName: b}]}
  // import d, { a, b as x } from "mod" => name = d, namedBinding: NamedImports = { elements: [{ name: a }, { name: x, propertyName: b}]}
  // @kind(SyntaxKind.ImportClause)
  trait ImportClause extends Declaration {
    name?: Identifier; // Default binding
    namedBindings?: NamespaceImport | NamedImports
  }

  // @kind(SyntaxKind.NamespaceImport)
  trait NamespaceImport extends Declaration {
    name: Identifier
  }

  // @kind(SyntaxKind.ExportDeclaration)
  trait ExportDeclaration extends DeclarationStatement {
    exportClause?: NamedExports
    moduleSpecifier?: Expression
  }

  // @kind(SyntaxKind.NamedImports)
  trait NamedImports extends Node {
    elements: NodeArray<ImportSpecifier>
  }

  // @kind(SyntaxKind.NamedExports)
  trait NamedExports extends Node {
    elements: NodeArray<ExportSpecifier>
  }

  type NamedImportsOrExports = NamedImports | NamedExports

  // @kind(SyntaxKind.ImportSpecifier)
  trait ImportSpecifier extends Declaration {
    propertyName?: Identifier;  // Name preceding "as" keyword (or () when "as" is absent)
    name: Identifier;       // Declared name
  }

  // @kind(SyntaxKind.ExportSpecifier)
  trait ExportSpecifier extends Declaration {
    propertyName?: Identifier;  // Name preceding "as" keyword (or () when "as" is absent)
    name: Identifier;       // Declared name
  }

  type ImportOrExportSpecifier = ImportSpecifier | ExportSpecifier

  // @kind(SyntaxKind.ExportAssignment)
  trait ExportAssignment extends DeclarationStatement {
    isExportEquals?: Boolean
    expression: Expression
  }

  trait FileReference extends TextRange {
    fileName: String
  }

  trait CommentRange extends TextRange {
    hasTrailingNewLine?: Boolean
    kind: SyntaxKind
  }

  // represents a top level: { type } expression in a JSDoc comment.
  // @kind(SyntaxKind.JSDocTypeExpression)
  trait JSDocTypeExpression extends Node {
    type: JSDocType
  }

  trait JSDocType extends TypeNode {
    _jsDocTypeBrand: any
  }

  // @kind(SyntaxKind.JSDocAllType)
  trait JSDocAllType extends JSDocType {
    _JSDocAllTypeBrand: any
  }

  // @kind(SyntaxKind.JSDocUnknownType)
  trait JSDocUnknownType extends JSDocType {
    _JSDocUnknownTypeBrand: any
  }

  // @kind(SyntaxKind.JSDocArrayType)
  trait JSDocArrayType extends JSDocType {
    elementType: JSDocType
  }

  // @kind(SyntaxKind.JSDocUnionType)
  trait JSDocUnionType extends JSDocType {
    types: NodeArray<JSDocType>
  }

  // @kind(SyntaxKind.JSDocTupleType)
  trait JSDocTupleType extends JSDocType {
    types: NodeArray<JSDocType>
  }

  // @kind(SyntaxKind.JSDocNonNullableType)
  trait JSDocNonNullableType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocNullableType)
  trait JSDocNullableType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocRecordType)
  trait JSDocRecordType extends JSDocType, TypeLiteralNode {
    members: NodeArray<JSDocRecordMember>
  }

  // @kind(SyntaxKind.JSDocTypeReference)
  trait JSDocTypeReference extends JSDocType {
    name: EntityName
    typeArguments: NodeArray<JSDocType>
  }

  // @kind(SyntaxKind.JSDocOptionalType)
  trait JSDocOptionalType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocFunctionType)
  trait JSDocFunctionType extends JSDocType, SignatureDeclaration {
    parameters: NodeArray<ParameterDeclaration>
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocVariadicType)
  trait JSDocVariadicType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocConstructorType)
  trait JSDocConstructorType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocThisType)
  trait JSDocThisType extends JSDocType {
    type: JSDocType
  }

  type JSDocTypeReferencingNode = JSDocThisType | JSDocConstructorType | JSDocVariadicType | JSDocOptionalType | JSDocNullableType | JSDocNonNullableType

  // @kind(SyntaxKind.JSDocRecordMember)
  trait JSDocRecordMember extends PropertySignature {
    name: Identifier | LiteralExpression
    type?: JSDocType
  }

  // @kind(SyntaxKind.JSDocComment)
  trait JSDocComment extends Node {
    tags: NodeArray<JSDocTag>
  }

  // @kind(SyntaxKind.JSDocTag)
  trait JSDocTag extends Node {
    atToken: Node
    tagName: Identifier
  }

  // @kind(SyntaxKind.JSDocTemplateTag)
  trait JSDocTemplateTag extends JSDocTag {
    typeParameters: NodeArray<TypeParameterDeclaration>
  }

  // @kind(SyntaxKind.JSDocReturnTag)
  trait JSDocReturnTag extends JSDocTag {
    typeExpression: JSDocTypeExpression
  }

  // @kind(SyntaxKind.JSDocTypeTag)
  trait JSDocTypeTag extends JSDocTag {
    typeExpression: JSDocTypeExpression
  }

  // @kind(SyntaxKind.JSDocParameterTag)
  trait JSDocParameterTag extends JSDocTag {
    preParameterName?: Identifier
    typeExpression?: JSDocTypeExpression
    postParameterName?: Identifier
    isBracketed: Boolean
  }

  trait AmdDependency {
    path: String
    name: String
  }

  // Source files are declarations when they are external modules.
  // @kind(SyntaxKind.SourceFile)
  trait SourceFile extends Declaration {
    statements: NodeArray<Statement>
    endOfFileToken: Node

    fileName: String
    /* internal */ path: Path
    text: String

    amdDependencies: Array[AmdDependency]
    moduleName: String
    referencedFiles: Array[FileReference]
    languageVariant: LanguageVariant
    isDeclarationFile: Boolean

    // this map is used by transpiler to supply alternative names for dependencies (i.e. in case of bundling)
    /* @internal */
    renamedDependencies?: Map<String>

    /**
     * lib.d.ts should have a reference comment like
     *
     *  /// <reference no-default-lib="true"/>
     *
     * If any other file has this comment, it signals not to include lib.d.ts
     * because this containing file is intended to act as a default library.
     */
    hasNoDefaultLib: Boolean

    languageVersion: ScriptTarget

    // The first node that causes this file to be an external module
    /* @internal */ externalModuleIndicator: Node
    // The first node that causes this file to be a CommonJS module
    /* @internal */ commonJsModuleIndicator: Node

    /* @internal */ identifiers: Map<String>
    /* @internal */ nodeCount: Int
    /* @internal */ identifierCount: Int
    /* @internal */ symbolCount: Int

    // File level diagnostics reported by the parser (includes diagnostics about /// references
    // as well as code diagnostics).
    /* @internal */ parseDiagnostics: Array[Diagnostic]

    // File level diagnostics reported by the binder.
    /* @internal */ bindDiagnostics: Array[Diagnostic]

    // Stores a line map for the file.
    // This field should never be used directly to obtain line map, use getLineMap def instead.
    /* @internal */ lineMap: Array[Int]
    /* @internal */ classifiableNames?: Map<String>
    // Stores a mapping 'external module reference text' -> 'resolved file name' | ()
    // It is used to resolve module names in the checker.
    // Content of this field should never be used directly - use getResolvedModuleFileName/setResolvedModuleFileName functions instead
    /* @internal */ resolvedModules: Map<ResolvedModule>
    /* @internal */ imports: Array[LiteralExpression]
    /* @internal */ moduleAugmentations: Array[LiteralExpression]
  }

  trait ScriptReferenceHost {
    getCompilerOptions(): CompilerOptions
    getSourceFile(fileName: String): SourceFile
    getCurrentDirectory(): String
  }

  trait ParseConfigHost {
    readDirectory(rootDir: String, extension: String, exclude: Array[String]): Array[String]
  }

  trait WriteFileCallback {
    (fileName: String, data: String, writeByteOrderMark: Boolean, onError?: (message: String) => Unit): Unit
  }

  class OperationCanceledException { }

  trait CancellationToken {
    isCancellationRequested(): Boolean

    /** @throws OperationCanceledException if isCancellationRequested is true */
    throwIfCancellationRequested(): Unit
  }

  trait Program extends ScriptReferenceHost {

    /**
     * Get a list of root file names that were passed to a 'createProgram'
     */
    getRootFileNames(): Array[String]

    /**
     * Get a list of files in the program
     */
    getSourceFiles(): Array[SourceFile]

    /**
     * Emits the JavaScript and declaration files.  If targetSourceFile is not specified, then
     * the JavaScript and declaration files will be produced for all the files in this program.
     * If targetSourceFile is specified, then only the JavaScript and declaration for that
     * specific file will be generated.
     *
     * If writeFile is not specified then the writeFile callback from the compiler host will be
     * used for writing the JavaScript and declaration files.  Otherwise, the writeFile parameter
     * will be invoked when writing the JavaScript and declaration files.
     */
    emit(targetSourceFile?: SourceFile, writeFile?: WriteFileCallback, cancellationToken?: CancellationToken): EmitResult

    getOptionsDiagnostics(cancellationToken?: CancellationToken): Array[Diagnostic]
    getGlobalDiagnostics(cancellationToken?: CancellationToken): Array[Diagnostic]
    getSyntacticDiagnostics(sourceFile?: SourceFile, cancellationToken?: CancellationToken): Array[Diagnostic]
    getSemanticDiagnostics(sourceFile?: SourceFile, cancellationToken?: CancellationToken): Array[Diagnostic]
    getDeclarationDiagnostics(sourceFile?: SourceFile, cancellationToken?: CancellationToken): Array[Diagnostic]

    /**
     * Gets a type checker that can be used to semantically analyze source fils in the program.
     */
    getTypeChecker(): TypeChecker

    /* @internal */ getCommonSourceDirectory(): String

    // For testing purposes only.  Should not be used by any other consumers (including the
    // language service).
    /* @internal */ getDiagnosticsProducingTypeChecker(): TypeChecker

    /* @internal */ getClassifiableNames(): Map<String>

    /* @internal */ getNodeCount(): Int
    /* @internal */ getIdentifierCount(): Int
    /* @internal */ getSymbolCount(): Int
    /* @internal */ getTypeCount(): Int

    /* @internal */ getFileProcessingDiagnostics(): DiagnosticCollection
    // For testing purposes only.
    /* @internal */ structureIsReused?: Boolean
  }

  trait SourceMapSpan {
    /** Line Int in the .js file. */
    emittedLine: Int
    /** Column Int in the .js file. */
    emittedColumn: Int
    /** Line Int in the .ts file. */
    sourceLine: Int
    /** Column Int in the .ts file. */
    sourceColumn: Int
    /** Optional name (index into names array) associated with this span. */
    nameIndex?: Int
    /** .ts file (index into sources array) associated with this span */
    sourceIndex: Int
  }

  trait SourceMapData {
    sourceMapFilePath: String;       // Where the sourcemap file is written
    jsSourceMappingURL: String;      // source map URL written in the .js file
    sourceMapFile: String;         // Source map's file field - .js file name
    sourceMapSourceRoot: String;     // Source map's sourceRoot field - location where the sources will be present if not ""
    sourceMapSources: Array[String];      // Source map's sources field - list of sources that can be indexed in this source map
    sourceMapSourcesContent?: Array[String];  // Source map's sourcesContent field - list of the sources' text to be embedded in the source map
    inputSourceFileNames: Array[String];    // Input source file (which one can use on program to get the file), 1:1 mapping with the sourceMapSources list
    sourceMapNames?: Array[String];       // Source map's names field - list of names that can be indexed in this source map
    sourceMapMappings: String;       // Source map's mapping field - encoded source map spans
    sourceMapDecodedMappings: Array[SourceMapSpan];  // Raw source map spans that were encoded into the sourceMapMappings
  }

  /** Return code used by getEmitOutput def to indicate status of the def */
  enum ExitStatus {
    // Compiler ran successfully.  Either this was a simple do-nothing compilation (for example,
    // when -version or -help was provided, or this was a normal compilation, no diagnostics
    // were produced, and all outputs were generated successfully.
    Success = 0,

    // Diagnostics were produced and because of them no code was generated.
    DiagnosticsPresent_OutputsSkipped = 1,

    // Diagnostics were produced and outputs were generated in spite of them.
    DiagnosticsPresent_OutputsGenerated = 2,
  }

  trait EmitResult {
    emitSkipped: Boolean
    diagnostics: Array[Diagnostic]
    /* @internal */ sourceMaps: Array[SourceMapData];  // Array of sourceMapData if compiler emitted sourcemaps
  }

  /* @internal */
  trait TypeCheckerHost {
    getCompilerOptions(): CompilerOptions

    getSourceFiles(): Array[SourceFile]
    getSourceFile(fileName: String): SourceFile
  }

  trait TypeChecker {
    getTypeOfSymbolAtLocation(symbol: Symbol, node: Node): Type
    getDeclaredTypeOfSymbol(symbol: Symbol): Type
    getPropertiesOfType(type: Type): Array[Symbol]
    getPropertyOfType(type: Type, propertyName: String): Symbol
    getSignaturesOfType(type: Type, kind: SignatureKind): Array[Signature]
    getIndexTypeOfType(type: Type, kind: IndexKind): Type
    getBaseTypes(type: InterfaceType): Array[ObjectType]
    getReturnTypeOfSignature(signature: Signature): Type

    getSymbolsInScope(location: Node, meaning: SymbolFlags): Array[Symbol]
    getSymbolAtLocation(node: Node): Symbol
    getSymbolsOfParameterPropertyDeclaration(parameter: ParameterDeclaration, parameterName: String): Array[Symbol]
    getShorthandAssignmentValueSymbol(location: Node): Symbol
    getExportSpecifierLocalTargetSymbol(location: ExportSpecifier): Symbol
    getTypeAtLocation(node: Node): Type
    typeToString(type: Type, enclosingDeclaration?: Node, flags?: TypeFormatFlags): String
    symbolToString(symbol: Symbol, enclosingDeclaration?: Node, meaning?: SymbolFlags): String
    getSymbolDisplayBuilder(): SymbolDisplayBuilder
    getFullyQualifiedName(symbol: Symbol): String
    getAugmentedPropertiesOfType(type: Type): Array[Symbol]
    getRootSymbols(symbol: Symbol): Array[Symbol]
    getContextualType(node: Expression): Type
    getResolvedSignature(node: CallLikeExpression, candidatesOutArray?: Array[Signature]): Signature
    getSignatureFromDeclaration(declaration: SignatureDeclaration): Signature
    isImplementationOfOverload(node: FunctionLikeDeclaration): Boolean
    isUndefinedSymbol(symbol: Symbol): Boolean
    isArgumentsSymbol(symbol: Symbol): Boolean
    isUnknownSymbol(symbol: Symbol): Boolean

    getConstantValue(node: EnumMember | PropertyAccessExpression | ElementAccessExpression): Int
    isValidPropertyAccess(node: PropertyAccessExpression | QualifiedName, propertyName: String): Boolean
    getAliasedSymbol(symbol: Symbol): Symbol
    getExportsOfModule(moduleSymbol: Symbol): Array[Symbol]

    getJsxElementAttributesType(elementNode: JsxOpeningLikeElement): Type
    getJsxIntrinsicTagNames(): Array[Symbol]
    isOptionalParameter(node: ParameterDeclaration): Boolean

    // Should not be called directly.  Should only be accessed through the Program instance.
    /* @internal */ getDiagnostics(sourceFile?: SourceFile, cancellationToken?: CancellationToken): Array[Diagnostic]
    /* @internal */ getGlobalDiagnostics(): Array[Diagnostic]
    /* @internal */ getEmitResolver(sourceFile?: SourceFile, cancellationToken?: CancellationToken): EmitResolver

    /* @internal */ getNodeCount(): Int
    /* @internal */ getIdentifierCount(): Int
    /* @internal */ getSymbolCount(): Int
    /* @internal */ getTypeCount(): Int
  }

  trait SymbolDisplayBuilder {
    buildTypeDisplay(type: Type, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): Unit
    buildSymbolDisplay(symbol: Symbol, writer: SymbolWriter, enclosingDeclaration?: Node, meaning?: SymbolFlags, flags?: SymbolFormatFlags): Unit
    buildSignatureDisplay(signatures: Signature, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags, kind?: SignatureKind): Unit
    buildParameterDisplay(parameter: Symbol, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): Unit
    buildTypeParameterDisplay(tp: TypeParameter, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): Unit
    buildTypeParameterDisplayFromSymbol(symbol: Symbol, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): Unit
    buildDisplayForParametersAndDelimiters(parameters: Array[Symbol], writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): Unit
    buildDisplayForTypeParametersAndDelimiters(typeParameters: Array[TypeParameter], writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): Unit
    buildReturnTypeDisplay(signature: Signature, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): Unit
  }

  trait SymbolWriter {
    writeKeyword(text: String): Unit
    writeOperator(text: String): Unit
    writePunctuation(text: String): Unit
    writeSpace(text: String): Unit
    writeStringLiteral(text: String): Unit
    writeParameter(text: String): Unit
    writeSymbol(text: String, symbol: Symbol): Unit
    writeLine(): Unit
    increaseIndent(): Unit
    decreaseIndent(): Unit
    clear(): Unit

    // Called when the symbol writer encounters a symbol to write.  Currently only used by the
    // declaration emitter to help determine if it should patch up the final declaration file
    // with import statements it previously saw (but chose not to emit).
    trackSymbol(symbol: Symbol, enclosingDeclaration?: Node, meaning?: SymbolFlags): Unit
    reportInaccessibleThisError(): Unit
  }

  val enum TypeFormatFlags {
    None              = 0x00000000,
    WriteArrayAsGenericType     = 0x00000001,  // Write Array<T> instead Array[T]
    UseTypeOfFunction         = 0x00000002,  // Write typeof instead of def type literal
    NoTruncation          = 0x00000004,  // Don't truncate typeToString result
    WriteArrowStyleSignature    = 0x00000008,  // Write arrow style signature
    WriteOwnNameForAnyLike      = 0x00000010,  // Write symbol's own name instead of 'any' for any like types (eg. unknown, __resolving__ etc)
    WriteTypeArgumentsOfSignature   = 0x00000020,  // Write the type arguments instead of type parameters of the signature
    InElementType           = 0x00000040,  // Writing an array or union element type
    UseFullyQualifiedType       = 0x00000080,  // Write out the fully qualified type name (eg. Module.Type, instead of Type)
  }

  val enum SymbolFormatFlags {
    None = 0x00000000,

    // Write symbols's type argument if it is instantiated symbol
    // eg. class C<T> { p: T }   <-- Show p as C<T>.p here
    //   var a: C<Int>
    //   var p = a.p;  <--- Here p is property of C<Int> so show it as C<Int>.p instead of just C.p
    WriteTypeParametersOrArguments = 0x00000001,

    // Use only external alias information to get the symbol name in the given context
    // eg.  module m { class c { } } import x = m.c
    // When this flag is specified m.c will be used to refer to the class instead of alias symbol x
    UseOnlyExternalAliasing = 0x00000002,
  }

  /* @internal */
  val enum SymbolAccessibility {
    Accessible,
    NotAccessible,
    CannotBeNamed
  }

  val enum TypePredicateKind {
    This,
    Identifier
  }

  trait TypePredicate {
    kind: TypePredicateKind
    type: Type
  }

  // @kind (TypePredicateKind.This)
  trait ThisTypePredicate extends TypePredicate {
    _thisTypePredicateBrand: any
  }

  // @kind (TypePredicateKind.Identifier)
  trait IdentifierTypePredicate extends TypePredicate {
    parameterName: String
    parameterIndex: Int
  }

  /* @internal */
  type AnyImportSyntax = ImportDeclaration | ImportEqualsDeclaration

  /* @internal */
  trait SymbolVisibilityResult {
    accessibility: SymbolAccessibility
    aliasesToMakeVisible?: Array[AnyImportSyntax]; // aliases that need to have this symbol visible
    errorSymbolName?: String; // Optional symbol name that results in error
    errorNode?: Node; // optional node that results in error
  }

  /* @internal */
  trait SymbolAccessibilityResult extends SymbolVisibilityResult {
    errorModuleName?: String; // If the symbol is not visible from module, module's name
  }

  /** Indicates how to serialize the name for a TypeReferenceNode when emitting decorator
    * metadata */
  /* @internal */
  enum TypeReferenceSerializationKind {
    Unknown,              // The TypeReferenceNode could not be resolved. The type name
                      // should be emitted using a safe fallback.
    TypeWithConstructSignatureAndValue, // The TypeReferenceNode resolves to a type with a constructor
                      // def that can be reached at runtime (e.g. a `class`
                      // declaration or a `var` declaration for the static side
                      // of a type, such as the global `Promise` type in lib.d.ts).
    VoidType,               // The TypeReferenceNode resolves to a Void-like type.
    NumberLikeType,           // The TypeReferenceNode resolves to a Number-like type.
    StringLikeType,           // The TypeReferenceNode resolves to a String-like type.
    BooleanType,            // The TypeReferenceNode resolves to a Boolean-like type.
    ArrayLikeType,            // The TypeReferenceNode resolves to an Array-like type.
    ESSymbolType,             // The TypeReferenceNode resolves to the ESSymbol type.
    TypeWithCallSignature,        // The TypeReferenceNode resolves to a Function type or a type
                      // with call signatures.
    ObjectType,             // The TypeReferenceNode resolves to any other type.
  }

  /* @internal */
  trait EmitResolver {
    hasGlobalName(name: String): Boolean
    getReferencedExportContainer(node: Identifier): SourceFile | ModuleDeclaration | EnumDeclaration
    getReferencedImportDeclaration(node: Identifier): Declaration
    getReferencedDeclarationWithCollidingName(node: Identifier): Declaration
    isDeclarationWithCollidingName(node: Declaration): Boolean
    isValueAliasDeclaration(node: Node): Boolean
    isReferencedAliasDeclaration(node: Node, checkChildren?: Boolean): Boolean
    isTopLevelValueImportEqualsWithEntityName(node: ImportEqualsDeclaration): Boolean
    getNodeCheckFlags(node: Node): NodeCheckFlags
    isDeclarationVisible(node: Declaration): Boolean
    collectLinkedAliases(node: Identifier): Array[Node]
    isImplementationOfOverload(node: FunctionLikeDeclaration): Boolean
    writeTypeOfDeclaration(declaration: AccessorDeclaration | VariableLikeDeclaration, enclosingDeclaration: Node, flags: TypeFormatFlags, writer: SymbolWriter): Unit
    writeReturnTypeOfSignatureDeclaration(signatureDeclaration: SignatureDeclaration, enclosingDeclaration: Node, flags: TypeFormatFlags, writer: SymbolWriter): Unit
    writeTypeOfExpression(expr: Expression, enclosingDeclaration: Node, flags: TypeFormatFlags, writer: SymbolWriter): Unit
    isSymbolAccessible(symbol: Symbol, enclosingDeclaration: Node, meaning: SymbolFlags): SymbolAccessibilityResult
    isEntityNameVisible(entityName: EntityName | Expression, enclosingDeclaration: Node): SymbolVisibilityResult
    // Returns the constant value this property access resolves to, or '()' for a non-constant
    getConstantValue(node: EnumMember | PropertyAccessExpression | ElementAccessExpression): Int
    getReferencedValueDeclaration(reference: Identifier): Declaration
    getTypeReferenceSerializationKind(typeName: EntityName): TypeReferenceSerializationKind
    isOptionalParameter(node: ParameterDeclaration): Boolean
    moduleExportsSomeValue(moduleReferenceExpression: Expression): Boolean
    isArgumentsLocalBinding(node: Identifier): Boolean
    getExternalModuleFileFromDeclaration(declaration: ImportEqualsDeclaration | ImportDeclaration | ExportDeclaration | ModuleDeclaration): SourceFile
  }

  val enum SymbolFlags {
    None          = 0,
    FunctionScopedVariable  = 0x00000001,  // Variable (var) or parameter
    BlockScopedVariable   = 0x00000002,  // A block-scoped variable (var or val)
    Property        = 0x00000004,  // Property or enum member
    EnumMember        = 0x00000008,  // Enum member
    Function        = 0x00000010,  // Function
    Class           = 0x00000020,  // Class
    Interface         = 0x00000040,  // Interface
    ConstEnum         = 0x00000080,  // Const enum
    RegularEnum       = 0x00000100,  // Enum
    ValueModule       = 0x00000200,  // Instantiated module
    NamespaceModule     = 0x00000400,  // Uninstantiated module
    TypeLiteral       = 0x00000800,  // Type Literal
    ObjectLiteral       = 0x00001000,  // Object Literal
    Method          = 0x00002000,  // Method
    Constructor       = 0x00004000,  // Constructor
    GetAccessor       = 0x00008000,  // Get accessor
    SetAccessor       = 0x00010000,  // Set accessor
    Signature         = 0x00020000,  // Call, construct, or index signature
    TypeParameter       = 0x00040000,  // Type parameter
    TypeAlias         = 0x00080000,  // Type alias
    ExportValue       = 0x00100000,  // Exported value marker (see comment in declareModuleMember in binder)
    ExportType        = 0x00200000,  // Exported type marker (see comment in declareModuleMember in binder)
    ExportNamespace     = 0x00400000,  // Exported package marker (see comment in declareModuleMember in binder)
    Alias           = 0x00800000,  // An alias for another symbol (see comment in isAliasSymbolDeclaration in checker)
    Instantiated      = 0x01000000,  // Instantiated symbol
    Merged          = 0x02000000,  // Merged symbol (created during program binding)
    Transient         = 0x04000000,  // Transient symbol (created during type check)
    Prototype         = 0x08000000,  // Prototype property (no source representation)
    SyntheticProperty     = 0x10000000,  // Property in union or intersection type
    Optional        = 0x20000000,  // Optional property
    ExportStar        = 0x40000000,  // Export * declaration

    Enum = RegularEnum | ConstEnum,
    Variable = FunctionScopedVariable | BlockScopedVariable,
    Value = Variable | Property | EnumMember | Function | Class | Enum | ValueModule | Method | GetAccessor | SetAccessor,
    Type = Class | Interface | Enum | TypeLiteral | ObjectLiteral | TypeParameter | TypeAlias,
    Namespace = ValueModule | NamespaceModule,
    Module = ValueModule | NamespaceModule,
    Accessor = GetAccessor | SetAccessor,

    // Variables can be redeclared, but can not redeclare a block-scoped declaration with the
    // same name, or any other value that is not a variable, e.g. ValueModule or Class
    FunctionScopedVariableExcludes = Value & ~FunctionScopedVariable,

    // Block-scoped declarations are not allowed to be re-declared
    // they can not merge with anything in the value space
    BlockScopedVariableExcludes = Value,

    ParameterExcludes = Value,
    PropertyExcludes = Value,
    EnumMemberExcludes = Value,
    FunctionExcludes = Value & ~(Function | ValueModule),
    ClassExcludes = (Value | Type) & ~(ValueModule | Interface), // class-trait mergability done in checker.ts
    InterfaceExcludes = Type & ~(Interface | Class),
    RegularEnumExcludes = (Value | Type) & ~(RegularEnum | ValueModule), // regular enums merge only with regular enums and modules
    ConstEnumExcludes = (Value | Type) & ~ConstEnum, // val enums merge only with val enums
    ValueModuleExcludes = Value & ~(Function | Class | RegularEnum | ValueModule),
    NamespaceModuleExcludes = 0,
    MethodExcludes = Value & ~Method,
    GetAccessorExcludes = Value & ~SetAccessor,
    SetAccessorExcludes = Value & ~GetAccessor,
    TypeParameterExcludes = Type & ~TypeParameter,
    TypeAliasExcludes = Type,
    AliasExcludes = Alias,

    ModuleMember = Variable | Function | Class | Interface | Enum | Module | TypeAlias | Alias,

    ExportHasLocal = Function | Class | Enum | ValueModule,

    HasExports = Class | Enum | Module,
    HasMembers = Class | Interface | TypeLiteral | ObjectLiteral,

    BlockScoped = BlockScopedVariable | Class | Enum,

    PropertyOrAccessor = Property | Accessor,
    Export = ExportNamespace | ExportType | ExportValue,

    /* @internal */
    // The set of things we consider semantically classifiable.  Used to speed up the LS during
    // classification.
    Classifiable = Class | Enum | TypeAlias | Interface | TypeParameter | Module,
  }

  trait Symbol {
    flags: SymbolFlags;           // Symbol flags
    name: String;               // Name of symbol
    declarations?: Array[Declaration];       // Declarations associated with this symbol
    valueDeclaration?: Declaration;     // First value declaration of the symbol

    members?: SymbolTable;          // Class, trait or literal instance members
    exports?: SymbolTable;          // Module exports
    /* @internal */ id?: Int;      // Unique id (used to look up SymbolLinks)
    /* @internal */ mergeId?: Int;     // Merge id (used to look up merged symbol)
    /* @internal */ parent?: Symbol;    // Parent symbol
    /* @internal */ exportSymbol?: Symbol;  // Exported symbol associated with this symbol
    /* @internal */ constEnumOnlyModule?: Boolean; // True if module contains only val enums or other modules with only val enums
  }

  /* @internal */
  trait SymbolLinks {
    target?: Symbol;          // Resolved (non-alias) target of an alias
    type?: Type;            // Type of value symbol
    declaredType?: Type;        // Type of class, trait, enum, type alias, or type parameter
    typeParameters?: Array[TypeParameter];   // Type parameters of type alias (() if non-generic)
    inferredClassType?: Type;       // Type of an inferred ES5 class
    instantiations?: Map<Type>;     // Instantiations of generic type alias (() if non-generic)
    mapper?: TypeMapper;        // Type mapper for instantiation alias
    referenced?: Boolean;         // True if alias symbol has been referenced as a value
    containingType?: UnionOrIntersectionType; // Containing union or intersection type for synthetic property
    resolvedExports?: SymbolTable;    // Resolved exports of module
    exportsChecked?: Boolean;       // True if exports of external module have been checked
    isDeclarationWithCollidingName?: Boolean;  // True if symbol is block scoped redeclaration
    bindingElement?: BindingElement;  // Binding element associated with property symbol
    exportsSomeValue?: Boolean;     // true if module exports some value (not just types)
  }

  /* @internal */
  trait TransientSymbol extends Symbol, SymbolLinks { }

  trait SymbolTable {
    [index: String]: Symbol
  }

  /* @internal */
  val enum NodeCheckFlags {
    TypeChecked             = 0x00000001,  // Node has been type checked
    LexicalThis             = 0x00000002,  // Lexical 'this' reference
    CaptureThis             = 0x00000004,  // Lexical 'this' used in body
    SuperInstance             = 0x00000100,  // Instance 'super' reference
    SuperStatic             = 0x00000200,  // Static 'super' reference
    ContextChecked            = 0x00000400,  // Contextual types have been assigned
    AsyncMethodWithSuper        = 0x00000800,  // An async method that reads a value from a member of 'super'.
    AsyncMethodWithSuperBinding     = 0x00001000,  // An async method that assigns a value to a member of 'super'.
    CaptureArguments          = 0x00002000,  // Lexical 'arguments' used in body (for async functions)
    EnumValuesComputed          = 0x00004000, // Values for enum members have been computed, and any errors have been reported for them.
    LexicalModuleMergesWithClass    = 0x00008000, // Instantiated lexical module declaration is merged with a previous class declaration.
    LoopWithCapturedBlockScopedBinding  = 0x00010000, // Loop that contains block scoped variable captured in closure
    CapturedBlockScopedBinding      = 0x00020000, // Block-scoped binding that is captured in some def
    BlockScopedBindingInLoop      = 0x00040000, // Block-scoped binding with declaration nested inside iteration statement
    HasSeenSuperCall          = 0x00080000, // Set during the binding when encounter 'super'
    ClassWithBodyScopedClassBinding   = 0x00100000, // Decorated class that contains a binding to itself inside of the class body.
    BodyScopedClassBinding        = 0x00200000, // Binding to a decorated class inside of the class's body.
    NeedsLoopOutParameter         = 0x00400000, // Block scoped binding whose value should be explicitly copied outside of the converted loop
  }

  /* @internal */
  trait NodeLinks {
    resolvedType?: Type;        // Cached type of type node
    resolvedAwaitedType?: Type;     // Cached awaited type of type node
    resolvedSignature?: Signature;  // Cached signature of signature node or call expression
    resolvedSymbol?: Symbol;      // Cached name resolution result
    resolvedIndexInfo?: IndexInfo;  // Cached indexing info resolution result
    flags?: NodeCheckFlags;       // Set of flags specific to Node
    enumMemberValue?: Int;     // Constant value of enum member
    isVisible?: Boolean;        // Is this node visible
    generatedName?: String;       // Generated name for module, enum, or import declaration
    generatedNames?: Map<String>;   // Generated names table for source file
    assignmentChecks?: Map<Boolean>;  // Cache of assignment checks
    hasReportedStatementInAmbientContext?: Boolean;  // Cache Boolean if we report statements in ambient context
    importOnRightSide?: Symbol;     // for import declarations - import that appear on the right side
    jsxFlags?: JsxFlags;        // flags for knowing what kind of element/attributes we're dealing with
    resolvedJsxType?: Type;       // resolved element attributes type of a JSX openinglike element
  }

  val enum TypeFlags {
    Any           = 0x00000001,
    String          = 0x00000002,
    Number          = 0x00000004,
    Boolean         = 0x00000008,
    Void          = 0x00000010,
    Undefined         = 0x00000020,
    Null          = 0x00000040,
    Enum          = 0x00000080,  // Enum type
    StringLiteral       = 0x00000100,  // String literal type
    TypeParameter       = 0x00000200,  // Type parameter
    Class           = 0x00000400,  // Class
    Interface         = 0x00000800,  // Interface
    Reference         = 0x00001000,  // Generic type reference
    Tuple           = 0x00002000,  // Tuple
    Union           = 0x00004000,  // Union (T | U)
    Intersection      = 0x00008000,  // Intersection (T & U)
    Anonymous         = 0x00010000,  // Anonymous
    Instantiated      = 0x00020000,  // Instantiated anonymous type
    /* @internal */
    FromSignature       = 0x00040000,  // Created for signature assignment check
    ObjectLiteral       = 0x00080000,  // Originates in an object literal
    /* @internal */
    FreshObjectLiteral    = 0x00100000,  // Fresh object literal type
    /* @internal */
    ContainsUndefinedOrNull = 0x00200000,  // Type is or contains Undefined or Null type
    /* @internal */
    ContainsObjectLiteral   = 0x00400000,  // Type is or contains object literal type
    /* @internal */
    ContainsAnyFunctionType = 0x00800000,  // Type is or contains object literal type
    ESSymbol        = 0x01000000,  // Type of symbol primitive introduced in ES6
    ThisType        = 0x02000000,  // This type
    ObjectLiteralPatternWithComputedProperties = 0x04000000,  // Object literal type implied by binding pattern has computed properties
    PredicateType       = 0x08000000,  // Predicate types are also Boolean types, but should not be considered Intrinsics - there's no way to capture this with flags

    /* @internal */
    Intrinsic = Any | String | Number | Boolean | ESSymbol | Void | Undefined | Null,
    /* @internal */
    Primitive = String | Number | Boolean | ESSymbol | Void | Undefined | Null | StringLiteral | Enum,
    StringLike = String | StringLiteral,
    NumberLike = Number | Enum,
    ObjectType = Class | Interface | Reference | Tuple | Anonymous,
    UnionOrIntersection = Union | Intersection,
    StructuredType = ObjectType | Union | Intersection,
    /* @internal */
    RequiresWidening = ContainsUndefinedOrNull | ContainsObjectLiteral | PredicateType,
    /* @internal */
    PropagatingFlags = ContainsUndefinedOrNull | ContainsObjectLiteral | ContainsAnyFunctionType
  }

  type DestructuringPattern = BindingPattern | ObjectLiteralExpression | ArrayLiteralExpression

  // Properties common to all types
  trait Type {
    flags: TypeFlags;        // Flags
    /* @internal */ id: Int;    // Unique ID
    symbol?: Symbol;         // Symbol associated with type (if any)
    pattern?: DestructuringPattern;  // Destructuring pattern represented by type (if any)
  }

  /* @internal */
  // Intrinsic types (TypeFlags.Intrinsic)
  trait IntrinsicType extends Type {
    intrinsicName: String;  // Name of intrinsic type
  }

  // Predicate types (TypeFlags.Predicate)
  trait PredicateType extends Type {
    predicate: ThisTypePredicate | IdentifierTypePredicate
  }

  // String literal types (TypeFlags.StringLiteral)
  trait StringLiteralType extends Type {
    text: String;  // Text of String literal
  }

  // Object types (TypeFlags.ObjectType)
  trait ObjectType extends Type { }

  // Class and trait types (TypeFlags.Class and TypeFlags.Interface)
  trait InterfaceType extends ObjectType {
    typeParameters: Array[TypeParameter];       // Type parameters (() if non-generic)
    outerTypeParameters: Array[TypeParameter];    // Outer type parameters (() if none)
    localTypeParameters: Array[TypeParameter];    // Local type parameters (() if none)
    thisType: TypeParameter;           // The "this" type (() if none)
    /* @internal */
    resolvedBaseConstructorType?: Type;    // Resolved base constructor type of class
    /* @internal */
    resolvedBaseTypes: Array[ObjectType];       // Resolved base types
  }

  trait InterfaceTypeWithDeclaredMembers extends InterfaceType {
    declaredProperties: Array[Symbol];        // Declared members
    declaredCallSignatures: Array[Signature];     // Declared call signatures
    declaredConstructSignatures: Array[Signature];  // Declared construct signatures
    declaredStringIndexInfo: IndexInfo;    // Declared String indexing info
    declaredNumberIndexInfo: IndexInfo;    // Declared numeric indexing info
  }

  // Type references (TypeFlags.Reference). When a class or trait has type parameters or
  // a "this" type, references to the class or trait are made using type references. The
  // typeArguments property specifies the types to substitute for the type parameters of the
  // class or trait and optionally includes an extra element that specifies the type to
  // substitute for "this" in the resulting instantiation. When no extra argument is present,
  // the type reference itself is substituted for "this". The typeArguments property is ()
  // if the class or trait has no type parameters and the reference isn't specifying an
  // explicit "this" argument.
  trait TypeReference extends ObjectType {
    target: GenericType;  // Type reference target
    typeArguments: Array[Type];  // Type reference type arguments (() if none)
  }

  // Generic class and trait types
  trait GenericType extends InterfaceType, TypeReference {
    /* @internal */
    instantiations: Map<TypeReference>;   // Generic instantiation cache
  }

  trait TupleType extends ObjectType {
    elementTypes: Array[Type];  // Element types
  }

  trait UnionOrIntersectionType extends Type {
    types: Array[Type];          // Constituent types
    /* @internal */
    reducedType: Type;        // Reduced union type (all subtypes removed)
    /* @internal */
    resolvedProperties: SymbolTable;  // Cache of resolved properties
  }

  trait UnionType extends UnionOrIntersectionType { }

  trait IntersectionType extends UnionOrIntersectionType { }

  /* @internal */
  // An instantiated anonymous type has a target and a mapper
  trait AnonymousType extends ObjectType {
    target?: AnonymousType;  // Instantiation target
    mapper?: TypeMapper;   // Instantiation mapper
  }

  /* @internal */
  // Resolved object, union, or intersection type
  trait ResolvedType extends ObjectType, UnionOrIntersectionType {
    members: SymbolTable;        // Properties by name
    properties: Array[Symbol];        // Properties
    callSignatures: Array[Signature];     // Call signatures of type
    constructSignatures: Array[Signature];  // Construct signatures of type
    stringIndexInfo?: IndexInfo;     // String indexing info
    numberIndexInfo?: IndexInfo;     // Numeric indexing info
  }

  /* @internal */
  // Object literals are initially marked fresh. Freshness disappears following an assignment,
  // before a type assertion, or when when an object literal's type is widened. The regular
  // version of a fresh type is identical except for the TypeFlags.FreshObjectLiteral flag.
  trait FreshObjectLiteralType extends ResolvedType {
    regularType: ResolvedType;  // Regular version of fresh type
  }

  // Just a place to cache element types of iterables and iterators
  /* @internal */
  trait IterableOrIteratorType extends ObjectType, UnionType {
    iterableElementType?: Type
    iteratorElementType?: Type
  }

  // Type parameters (TypeFlags.TypeParameter)
  trait TypeParameter extends Type {
    constraint: Type;    // Constraint
    /* @internal */
    target?: TypeParameter;  // Instantiation target
    /* @internal */
    mapper?: TypeMapper;   // Instantiation mapper
    /* @internal */
    resolvedApparentType: Type
  }

  val enum SignatureKind {
    Call,
    Construct,
  }

  trait Signature {
    declaration: SignatureDeclaration;  // Originating declaration
    typeParameters: Array[TypeParameter];  // Type parameters (() if non-generic)
    parameters: Array[Symbol];         // Parameters
    /* @internal */
    resolvedReturnType: Type;       // Resolved return type
    /* @internal */
    minArgumentCount: Int;       // Number of non-optional parameters
    /* @internal */
    hasRestParameter: Boolean;      // True if last parameter is rest parameter
    /* @internal */
    hasStringLiterals: Boolean;     // True if specialized
    /* @internal */
    target?: Signature;         // Instantiation target
    /* @internal */
    mapper?: TypeMapper;        // Instantiation mapper
    /* @internal */
    unionSignatures?: Array[Signature];    // Underlying signatures of a union signature
    /* @internal */
    erasedSignatureCache?: Signature;   // Erased version of signature (deferred)
    /* @internal */
    isolatedSignatureType?: ObjectType; // A manufactured type that just contains the signature for purposes of signature comparison
  }

  val enum IndexKind {
    String,
    Number,
  }

  trait IndexInfo {
    type: Type
    isReadonly: Boolean
    declaration?: SignatureDeclaration
  }

  /* @internal */
  trait TypeMapper {
    (t: TypeParameter): Type
    instantiations?: Array[Type];  // Cache of instantiations created using this type mapper.
    context?: InferenceContext; // The inference context this mapper was created from.
                  // Only inference mappers have this set (in createInferenceMapper).
                  // The identity mapper and regular instantiation mappers do not need it.
  }

  /* @internal */
  trait TypeInferences {
    primary: Array[Type];  // Inferences made directly to a type parameter
    secondary: Array[Type];  // Inferences made to a type parameter in a union type
    isFixed: Boolean;   // Whether the type parameter is fixed, as defined in section 4.12.2 of the TypeScript spec
              // If a type parameter is fixed, no more inferences can be made for the type parameter
  }

  /* @internal */
  trait InferenceContext {
    typeParameters: Array[TypeParameter];  // Type parameters for which inferences are made
    inferUnionTypes: Boolean;       // Infer union types for disjoint candidates (otherwise undefinedType)
    inferences: Array[TypeInferences];     // Inferences made for each type parameter
    inferredTypes: Array[Type];        // Inferred type for each type parameter
    mapper?: TypeMapper;        // Type mapper for this inference context
    failedTypeParameterIndex?: Int;  // Index of type parameter for which inference failed
    // It is optional because in contextual signature instantiation, nothing fails
  }

  /* @internal */
  val enum SpecialPropertyAssignmentKind {
    None,
    /// exports.name = expr
    ExportsProperty,
    /// module.exports = expr
    ModuleExports,
    /// className.prototype.name = expr
    PrototypeProperty,
    /// this.name = expr
    ThisProperty
  }

  trait DiagnosticMessage {
    key: String
    category: DiagnosticCategory
    code: Int
    message: String
  }

  /**
   * A linked list of formatted diagnostic messages to be used as part of a multiline message.
   * It is built from the bottom up, leaving the head to be the "main" diagnostic.
   * While it seems that DiagnosticMessageChain is structurally similar to DiagnosticMessage,
   * the difference is that messages are all preformatted in DMC.
   */
  trait DiagnosticMessageChain {
    messageText: String
    category: DiagnosticCategory
    code: Int
    next?: DiagnosticMessageChain
  }

  trait Diagnostic {
    file: SourceFile
    start: Int
    length: Int
    messageText: String | DiagnosticMessageChain
    category: DiagnosticCategory
    code: Int
  }

  enum DiagnosticCategory {
    Warning,
    Error,
    Message,
  }

  enum ModuleResolutionKind {
    Classic  = 1,
    NodeJs   = 2
  }

  type RootPaths = Array[String]
  type PathSubstitutions = Map<Array[String]>
  type TsConfigOnlyOptions = RootPaths | PathSubstitutions

  trait CompilerOptions {
    allowNonTsExtensions?: Boolean
    charset?: String
    declaration?: Boolean
    diagnostics?: Boolean
    emitBOM?: Boolean
    help?: Boolean
    init?: Boolean
    inlineSourceMap?: Boolean
    inlineSources?: Boolean
    jsx?: JsxEmit
    reactNamespace?: String
    listFiles?: Boolean
    locale?: String
    mapRoot?: String
    module?: ModuleKind
    newLine?: NewLineKind
    noEmit?: Boolean
    noEmitHelpers?: Boolean
    noEmitOnError?: Boolean
    noErrorTruncation?: Boolean
    noImplicitAny?: Boolean
    noLib?: Boolean
    noResolve?: Boolean
    out?: String
    outFile?: String
    outDir?: String
    preserveConstEnums?: Boolean
    /* @internal */ pretty?: DiagnosticStyle
    project?: String
    removeComments?: Boolean
    rootDir?: String
    sourceMap?: Boolean
    sourceRoot?: String
    suppressExcessPropertyErrors?: Boolean
    suppressImplicitAnyIndexErrors?: Boolean
    target?: ScriptTarget
    version?: Boolean
    watch?: Boolean
    isolatedModules?: Boolean
    experimentalDecorators?: Boolean
    emitDecoratorMetadata?: Boolean
    moduleResolution?: ModuleResolutionKind
    allowUnusedLabels?: Boolean
    allowUnreachableCode?: Boolean
    noImplicitReturns?: Boolean
    noFallthroughCasesInSwitch?: Boolean
    forceConsistentCasingInFileNames?: Boolean
    baseUrl?: String
    paths?: PathSubstitutions
    rootDirs?: RootPaths
    traceModuleResolution?: Boolean
    allowSyntheticDefaultImports?: Boolean
    allowJs?: Boolean
    noImplicitUseStrict?: Boolean
    /* @internal */ stripInternal?: Boolean

    // Skip checking lib.d.ts to help speed up tests.
    /* @internal */ skipDefaultLibCheck?: Boolean
    // Do not perform validation of output file name in transpile scenarios
    /* @internal */ suppressOutputPathCheck?: Boolean

    [option: String]: String | Int | Boolean | TsConfigOnlyOptions
  }

  enum ModuleKind {
    None = 0,
    CommonJS = 1,
    AMD = 2,
    UMD = 3,
    System = 4,
    ES6 = 5,
    ES2015 = ES6,
  }

  val enum JsxEmit {
    None = 0,
    Preserve = 1,
    React = 2
  }

  val enum NewLineKind {
    CarriageReturnLineFeed = 0,
    LineFeed = 1,
  }

  trait LineAndCharacter {
    line: Int
    /*
     * This value denotes the character position in line and is different from the 'column' because of tab characters.
     */
    character: Int
  }

  val enum ScriptTarget {
    ES3 = 0,
    ES5 = 1,
    ES6 = 2,
    ES2015 = ES6,
    Latest = ES6,
  }

  val enum LanguageVariant {
    Standard,
    JSX,
  }

  /* @internal */
  val enum DiagnosticStyle {
    Simple,
    Pretty,
  }

  trait ParsedCommandLine {
    options: CompilerOptions
    fileNames: Array[String]
    errors: Array[Diagnostic]
  }

  /* @internal */
  trait CommandLineOptionBase {
    name: String
    type: "String" | "Int" | "Boolean" | "object" | Map<Int>;  // a value of a primitive type, or an object literal mapping named values to actual values
    isFilePath?: Boolean;                   // True if option value is a path or fileName
    shortName?: String;                   // A short mnemonic for convenience - for instance, 'h' can be used in place of 'help'
    description?: DiagnosticMessage;            // The message describing what the command line switch does
    paramType?: DiagnosticMessage;              // The name to be used for a non-Boolean option's parameter
    experimental?: Boolean
    isTSConfigOnly?: Boolean;                 // True if option can only be specified via tsconfig.json file
  }

  /* @internal */
  trait CommandLineOptionOfPrimitiveType extends CommandLineOptionBase {
    type: "String" | "Int" | "Boolean"
  }

  /* @internal */
  trait CommandLineOptionOfCustomType extends CommandLineOptionBase {
    type: Map<Int>;       // an object literal mapping named values to actual values
    error: DiagnosticMessage;    // The error given when the argument does not fit a customized 'type'
  }

  /* @internal */
  trait TsConfigOnlyOption extends CommandLineOptionBase {
    type: "object"
  }

  /* @internal */
  type CommandLineOption = CommandLineOptionOfCustomType | CommandLineOptionOfPrimitiveType | TsConfigOnlyOption

  /* @internal */
  val enum CharacterCodes {
    nullCharacter = 0,
    maxAsciiCharacter = 0x7F,

    lineFeed = 0x0A,        // \n
    carriageReturn = 0x0D,    // \r
    lineSeparator = 0x2028,
    paragraphSeparator = 0x2029,
    nextLine = 0x0085,

    // Unicode 3.0 space characters
    space = 0x0020,   // " "
    nonBreakingSpace = 0x00A0,   //
    enQuad = 0x2000,
    emQuad = 0x2001,
    enSpace = 0x2002,
    emSpace = 0x2003,
    threePerEmSpace = 0x2004,
    fourPerEmSpace = 0x2005,
    sixPerEmSpace = 0x2006,
    figureSpace = 0x2007,
    punctuationSpace = 0x2008,
    thinSpace = 0x2009,
    hairSpace = 0x200A,
    zeroWidthSpace = 0x200B,
    narrowNoBreakSpace = 0x202F,
    ideographicSpace = 0x3000,
    mathematicalSpace = 0x205F,
    ogham = 0x1680,

    _ = 0x5F,
    $ = 0x24,

    _0 = 0x30,
    _1 = 0x31,
    _2 = 0x32,
    _3 = 0x33,
    _4 = 0x34,
    _5 = 0x35,
    _6 = 0x36,
    _7 = 0x37,
    _8 = 0x38,
    _9 = 0x39,

    a = 0x61,
    b = 0x62,
    c = 0x63,
    d = 0x64,
    e = 0x65,
    f = 0x66,
    g = 0x67,
    h = 0x68,
    i = 0x69,
    j = 0x6A,
    k = 0x6B,
    l = 0x6C,
    m = 0x6D,
    n = 0x6E,
    o = 0x6F,
    p = 0x70,
    q = 0x71,
    r = 0x72,
    s = 0x73,
    t = 0x74,
    u = 0x75,
    v = 0x76,
    w = 0x77,
    x = 0x78,
    y = 0x79,
    z = 0x7A,

    A = 0x41,
    B = 0x42,
    C = 0x43,
    D = 0x44,
    E = 0x45,
    F = 0x46,
    G = 0x47,
    H = 0x48,
    I = 0x49,
    J = 0x4A,
    K = 0x4B,
    L = 0x4C,
    M = 0x4D,
    N = 0x4E,
    O = 0x4F,
    P = 0x50,
    Q = 0x51,
    R = 0x52,
    S = 0x53,
    T = 0x54,
    U = 0x55,
    V = 0x56,
    W = 0x57,
    X = 0x58,
    Y = 0x59,
    Z = 0x5a,

    ampersand = 0x26,       // &
    asterisk = 0x2A,        // *
    at = 0x40,          // @
    backslash = 0x5C,       // \
    backtick = 0x60,        // `
    bar = 0x7C,           // |
    caret = 0x5E,         // ^
    closeBrace = 0x7D,      // }
    closeBracket = 0x5D,      // ]
    closeParen = 0x29,      // )
    colon = 0x3A,         // :
    comma = 0x2C,         // ,
    dot = 0x2E,           // .
    doubleQuote = 0x22,       // "
    equals = 0x3D,        // =
    exclamation = 0x21,       // !
    greaterThan = 0x3E,       // >
    hash = 0x23,          // #
    lessThan = 0x3C,        // <
    minus = 0x2D,         // -
    openBrace = 0x7B,       // {
    openBracket = 0x5B,       // [
    openParen = 0x28,       // (
    percent = 0x25,         // %
    plus = 0x2B,          // +
    question = 0x3F,        // ?
    semicolon = 0x3B,       //
    singleQuote = 0x27,       // '
    slash = 0x2F,         // /
    tilde = 0x7E,         // ~

    backspace = 0x08,       // \b
    formFeed = 0x0C,        // \f
    byteOrderMark = 0xFEFF,
    tab = 0x09,           // \t
    verticalTab = 0x0B,       // \v
  }

  trait ModuleResolutionHost {
    fileExists(fileName: String): Boolean
    // readFile def is used to read arbitrary text files on disk, i.e. when resolution procedure needs the content of 'package.json'
    // to determine location of bundled typings for node module
    readFile(fileName: String): String
    trace?(s: String): Unit
    directoryExists?(directoryName: String): Boolean
  }

  trait ResolvedModule {
    resolvedFileName: String
    /*
     * Denotes if 'resolvedFileName' is isExternalLibraryImport and thus should be proper external module:
     * - be a .d.ts file
     * - use top level imports\exports
     * - don't use tripleslash references
     */
    isExternalLibraryImport?: Boolean
  }

  trait ResolvedModuleWithFailedLookupLocations {
    resolvedModule: ResolvedModule
    failedLookupLocations: Array[String]
  }

  trait CompilerHost extends ModuleResolutionHost {
    getSourceFile(fileName: String, languageVersion: ScriptTarget, onError?: (message: String) => Unit): SourceFile
    getCancellationToken?(): CancellationToken
    getDefaultLibFileName(options: CompilerOptions): String
    writeFile: WriteFileCallback
    getCurrentDirectory(): String
    getCanonicalFileName(fileName: String): String
    useCaseSensitiveFileNames(): Boolean
    getNewLine(): String

    /*
     * CompilerHost must either implement resolveModuleNames (in case if it wants to be completely in charge of
     * module name resolution) or provide implementation for methods from ModuleResolutionHost (in this case compiler
     * will apply built-in module resolution logic and use members of ModuleResolutionHost to ask host specific questions).
     * If resolveModuleNames is implemented then implementation for members from ModuleResolutionHost can be just
     * 'throw new Error("NotImplemented")'
     */
    resolveModuleNames?(moduleNames: Array[String], containingFile: String): Array[ResolvedModule]
  }

  trait TextSpan {
    start: Int
    length: Int
  }

  trait TextChangeRange {
    span: TextSpan
    newLength: Int
  }

  /* @internal */
  trait DiagnosticCollection {
    // Adds a diagnostic to this diagnostic collection.
    add(diagnostic: Diagnostic): Unit

    // Gets all the diagnostics that aren't associated with a file.
    getGlobalDiagnostics(): Array[Diagnostic]

    // If fileName is provided, gets all the diagnostics associated with that file name.
    // Otherwise, returns all the diagnostics (global and file associated) in this collection.
    getDiagnostics(fileName?: String): Array[Diagnostic]

    // Gets a count of how many times this collection has been modified.  This value changes
    // each time 'add' is called (regardless of whether or not an equivalent diagnostic was
    // already in the collection).  As such, it can be used as a simple way to tell if any
    // operation caused diagnostics to be returned by storing and comparing the return value
    // of this method before/after the operation is performed.
    getModificationCount(): Int

    /* @internal */ reattachFileDiagnostics(newFile: SourceFile): Unit
  }
}
