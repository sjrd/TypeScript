package ts {
  interface Map<T> {
    [index: String]: T
  }

  // branded String type used to store absolute, normalized and canonicalized paths
  // arbitrary file name can be converted to Path via toPath def
  type Path = String & { __pathBrand: any }

  interface FileMap<T> {
    get(fileName: Path): T
    set(fileName: Path, value: T): void
    contains(fileName: Path): Boolean
    remove(fileName: Path): void

    forEachValue(f: (key: Path, v: T) => void): void
    clear(): void
  }

  interface TextRange {
    pos: Int
    end: Int
  }

  // token > SyntaxKind.Identifer => token is a keyword
  // Also, If you add a new SyntaxKind be sure to keep the `Markers` section at the bottom in sync
  val enum SyntaxKind {
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
    Count,
    // Markers
    FirstAssignment = EqualsToken,
    LastAssignment = CaretEqualsToken,
    FirstReservedWord = BreakKeyword,
    LastReservedWord = WithKeyword,
    FirstKeyword = BreakKeyword,
    LastKeyword = OfKeyword,
    FirstFutureReservedWord = ImplementsKeyword,
    LastFutureReservedWord = YieldKeyword,
    FirstTypeNode = TypePredicate,
    LastTypeNode = StringLiteralType,
    FirstPunctuation = OpenBraceToken,
    LastPunctuation = CaretEqualsToken,
    FirstToken = Unknown,
    LastToken = LastKeyword,
    FirstTriviaToken = SingleLineCommentTrivia,
    LastTriviaToken = ConflictMarkerTrivia,
    FirstLiteralToken = NumericLiteral,
    LastLiteralToken = NoSubstitutionTemplateLiteral,
    FirstTemplateToken = NoSubstitutionTemplateLiteral,
    LastTemplateToken = TemplateTail,
    FirstBinaryOperator = LessThanToken,
    LastBinaryOperator = CaretEqualsToken,
    FirstNode = QualifiedName,
  }

  val enum NodeFlags {
    None =         0,
    Export =       1 << 0,  // Declarations
    Ambient =      1 << 1,  // Declarations
    Public =       1 << 2,  // Property/Method
    Private =      1 << 3,  // Property/Method
    Protected =      1 << 4,  // Property/Method
    Static =       1 << 5,  // Property/Method
    Readonly =       1 << 6,  // Property/Method
    Abstract =       1 << 7,  // Class/Method/ConstructSignature
    Async =        1 << 8,  // Property/Method/Function
    Default =      1 << 9,  // Function/Class (export default declaration)
    Let =        1 << 10,  // Variable declaration
    Const =        1 << 11,  // Variable declaration
    Namespace =      1 << 12,  // Namespace declaration
    ExportContext =    1 << 13,  // Export context (initialized by binding)
    ContainsThis =     1 << 14,  // Interface contains references to "this"
    HasImplicitReturn =  1 << 15,  // If def implicitly returns on one of codepaths (initialized by binding)
    HasExplicitReturn =  1 << 16,  // If def has explicit reachable return on one of codepaths (initialized by binding)
    GlobalAugmentation = 1 << 17,  // Set if module declaration is an augmentation for the global scope
    HasClassExtends =  1 << 18,  // If the file has a non-ambient class with an extends clause in ES5 or lower (initialized by binding)
    HasDecorators =    1 << 19,  // If the file has decorators (initialized by binding)
    HasParamDecorators = 1 << 20,  // If the file has parameter decorators (initialized by binding)
    HasAsyncFunctions =  1 << 21,  // If the file has async functions (initialized by binding)
    DisallowInContext =  1 << 22,  // If node was parsed in a context where 'in-expressions' are not allowed
    YieldContext =     1 << 23,  // If node was parsed in the 'yield' context created when parsing a generator
    DecoratorContext =   1 << 24,  // If node was parsed as part of a decorator
    AwaitContext =     1 << 25,  // If node was parsed in the 'await' context created when parsing an async def
    ThisNodeHasError =   1 << 26,  // If the parser encountered an error when parsing the code that created this node
    JavaScriptFile =   1 << 27,  // If node was parsed in a JavaScript
    ThisNodeOrAnySubNodesHasError = 1 << 28,  // If this node or any of its children had an error
    HasAggregatedChildData = 1 << 29,  // If we've computed data from children and cached it in this node

    Modifier = Export | Ambient | Public | Private | Protected | Static | Abstract | Default | Async,
    AccessibilityModifier = Public | Private | Protected,
    BlockScoped = Let | Const,

    ReachabilityCheckFlags = HasImplicitReturn | HasExplicitReturn,
    EmitHelperFlags = HasClassExtends | HasDecorators | HasParamDecorators | HasAsyncFunctions,

    // Parsing context flags
    ContextFlags = DisallowInContext | YieldContext | DecoratorContext | AwaitContext,

    // Exclude these flags when parsing a Type
    TypeExcludesFlags = YieldContext | AwaitContext,
  }

  val enum JsxFlags {
    None = 0,
    /** An element from a named property of the JSX.IntrinsicElements interface */
    IntrinsicNamedElement = 1 << 0,
    /** An element inferred from the String index signature of the JSX.IntrinsicElements interface */
    IntrinsicIndexedElement = 1 << 1,

    IntrinsicElement = IntrinsicNamedElement | IntrinsicIndexedElement,
  }


  /* @internal */
  val enum RelationComparisonResult {
    Succeeded = 1, // Should be truthy
    Failed = 2,
    FailedAndReported = 3
  }

  interface Node extends TextRange {
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

  interface NodeArray<T> extends Array<T>, TextRange {
    hasTrailingComma?: Boolean
  }

  interface ModifiersArray extends NodeArray<Modifier> {
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
  interface Modifier extends Node { }

  // @kind(SyntaxKind.Identifier)
  interface Identifier extends PrimaryExpression {
    text: String;                  // Text of identifier (with escapes converted to characters)
    originalKeywordKind?: SyntaxKind;        // Original syntaxKind which get set so that we can report an error later
  }

  // @kind(SyntaxKind.QualifiedName)
  interface QualifiedName extends Node {
    // Must have same layout as PropertyAccess
    left: EntityName
    right: Identifier
  }

  type EntityName = Identifier | QualifiedName

  type PropertyName = Identifier | LiteralExpression | ComputedPropertyName

  type DeclarationName = Identifier | LiteralExpression | ComputedPropertyName | BindingPattern

  interface Declaration extends Node {
    _declarationBrand: any
    name?: DeclarationName
  }

  interface DeclarationStatement extends Declaration, Statement {
    name?: Identifier
  }

  // @kind(SyntaxKind.ComputedPropertyName)
  interface ComputedPropertyName extends Node {
    expression: Expression
  }

  // @kind(SyntaxKind.Decorator)
  interface Decorator extends Node {
    expression: LeftHandSideExpression
  }

  // @kind(SyntaxKind.TypeParameter)
  interface TypeParameterDeclaration extends Declaration {
    name: Identifier
    constraint?: TypeNode

    // For error recovery purposes.
    expression?: Expression
  }

  interface SignatureDeclaration extends Declaration {
    name?: PropertyName
    typeParameters?: NodeArray<TypeParameterDeclaration>
    parameters: NodeArray<ParameterDeclaration>
    type?: TypeNode
  }

  // @kind(SyntaxKind.CallSignature)
  interface CallSignatureDeclaration extends SignatureDeclaration, TypeElement { }

  // @kind(SyntaxKind.ConstructSignature)
  interface ConstructSignatureDeclaration extends SignatureDeclaration, TypeElement { }

  // @kind(SyntaxKind.VariableDeclaration)
  interface VariableDeclaration extends Declaration {
    parent?: VariableDeclarationList
    name: Identifier | BindingPattern;  // Declared variable name
    type?: TypeNode;          // Optional type annotation
    initializer?: Expression;       // Optional initializer
  }

  // @kind(SyntaxKind.VariableDeclarationList)
  interface VariableDeclarationList extends Node {
    declarations: NodeArray<VariableDeclaration>
  }

  // @kind(SyntaxKind.Parameter)
  interface ParameterDeclaration extends Declaration {
    dotDotDotToken?: Node;        // Present on rest parameter
    name: Identifier | BindingPattern;  // Declared parameter name
    questionToken?: Node;         // Present on optional parameter
    type?: TypeNode;          // Optional type annotation
    initializer?: Expression;       // Optional initializer
  }

  // @kind(SyntaxKind.BindingElement)
  interface BindingElement extends Declaration {
    propertyName?: PropertyName;    // Binding property name (in object binding pattern)
    dotDotDotToken?: Node;        // Present on rest binding element
    name: Identifier | BindingPattern;  // Declared binding element name
    initializer?: Expression;       // Optional initializer
  }

  // @kind(SyntaxKind.PropertySignature)
  interface PropertySignature extends TypeElement {
    name: PropertyName;         // Declared property name
    questionToken?: Node;         // Present on optional property
    type?: TypeNode;          // Optional type annotation
    initializer?: Expression;       // Optional initializer
  }

  // @kind(SyntaxKind.PropertyDeclaration)
  interface PropertyDeclaration extends ClassElement {
    questionToken?: Node;         // Present for use with reporting a grammar error
    name: PropertyName
    type?: TypeNode
    initializer?: Expression;       // Optional initializer
  }

  interface ObjectLiteralElement extends Declaration {
    _objectLiteralBrandBrand: any
    name?: PropertyName
   }

  // @kind(SyntaxKind.PropertyAssignment)
  interface PropertyAssignment extends ObjectLiteralElement {
    _propertyAssignmentBrand: any
    name: PropertyName
    questionToken?: Node
    initializer: Expression
  }

  // @kind(SyntaxKind.ShorthandPropertyAssignment)
  interface ShorthandPropertyAssignment extends ObjectLiteralElement {
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
  interface VariableLikeDeclaration extends Declaration {
    propertyName?: PropertyName
    dotDotDotToken?: Node
    name: DeclarationName
    questionToken?: Node
    type?: TypeNode
    initializer?: Expression
  }

  interface PropertyLikeDeclaration extends Declaration {
    name: PropertyName
  }

  interface BindingPattern extends Node {
    elements: NodeArray<BindingElement>
  }

  // @kind(SyntaxKind.ObjectBindingPattern)
  interface ObjectBindingPattern extends BindingPattern { }

  // @kind(SyntaxKind.ArrayBindingPattern)
  interface ArrayBindingPattern extends BindingPattern { }

  /**
   * Several node kinds share def-like features such as a signature,
   * a name, and a body. These nodes should extend FunctionLikeDeclaration.
   * Examples:
   * - FunctionDeclaration
   * - MethodDeclaration
   * - AccessorDeclaration
   */
  interface FunctionLikeDeclaration extends SignatureDeclaration {
    _functionLikeDeclarationBrand: any

    asteriskToken?: Node
    questionToken?: Node
    body?: Block | Expression
  }

  // @kind(SyntaxKind.FunctionDeclaration)
  interface FunctionDeclaration extends FunctionLikeDeclaration, DeclarationStatement {
    name?: Identifier
    body?: FunctionBody
  }

  // @kind(SyntaxKind.MethodSignature)
  interface MethodSignature extends SignatureDeclaration, TypeElement {
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
  interface MethodDeclaration extends FunctionLikeDeclaration, ClassElement, ObjectLiteralElement {
    name: PropertyName
    body?: FunctionBody
  }

  // @kind(SyntaxKind.Constructor)
  interface ConstructorDeclaration extends FunctionLikeDeclaration, ClassElement {
    body?: FunctionBody
  }

  // For when we encounter a semicolon in a class declaration.  ES6 allows these as class elements.
  // @kind(SyntaxKind.SemicolonClassElement)
  interface SemicolonClassElement extends ClassElement {
    _semicolonClassElementBrand: any
  }

  // See the comment on MethodDeclaration for the intuition behind AccessorDeclaration being a
  // ClassElement and an ObjectLiteralElement.
  interface AccessorDeclaration extends FunctionLikeDeclaration, ClassElement, ObjectLiteralElement {
    _accessorDeclarationBrand: any
    name: PropertyName
    body: FunctionBody
  }

  // @kind(SyntaxKind.GetAccessor)
  interface GetAccessorDeclaration extends AccessorDeclaration { }

  // @kind(SyntaxKind.SetAccessor)
  interface SetAccessorDeclaration extends AccessorDeclaration { }

  // @kind(SyntaxKind.IndexSignature)
  interface IndexSignatureDeclaration extends SignatureDeclaration, ClassElement, TypeElement {
    _indexSignatureDeclarationBrand: any
  }

  // @kind(SyntaxKind.AnyKeyword)
  // @kind(SyntaxKind.NumberKeyword)
  // @kind(SyntaxKind.BooleanKeyword)
  // @kind(SyntaxKind.StringKeyword)
  // @kind(SyntaxKind.SymbolKeyword)
  // @kind(SyntaxKind.VoidKeyword)
  interface TypeNode extends Node {
    _typeNodeBrand: any
  }

  // @kind(SyntaxKind.ThisType)
  interface ThisTypeNode extends TypeNode {
    _thisTypeNodeBrand: any
  }

  interface FunctionOrConstructorTypeNode extends TypeNode, SignatureDeclaration {
    _functionOrConstructorTypeNodeBrand: any
  }

  // @kind(SyntaxKind.FunctionType)
  interface FunctionTypeNode extends FunctionOrConstructorTypeNode { }

  // @kind(SyntaxKind.ConstructorType)
  interface ConstructorTypeNode extends FunctionOrConstructorTypeNode { }

  // @kind(SyntaxKind.TypeReference)
  interface TypeReferenceNode extends TypeNode {
    typeName: EntityName
    typeArguments?: NodeArray<TypeNode>
  }

  // @kind(SyntaxKind.TypePredicate)
  interface TypePredicateNode extends TypeNode {
    parameterName: Identifier | ThisTypeNode
    type: TypeNode
  }

  // @kind(SyntaxKind.TypeQuery)
  interface TypeQueryNode extends TypeNode {
    exprName: EntityName
  }

  // A TypeLiteral is the declaration node for an anonymous symbol.
  // @kind(SyntaxKind.TypeLiteral)
  interface TypeLiteralNode extends TypeNode, Declaration {
    members: NodeArray<TypeElement>
  }

  // @kind(SyntaxKind.ArrayType)
  interface ArrayTypeNode extends TypeNode {
    elementType: TypeNode
  }

  // @kind(SyntaxKind.TupleType)
  interface TupleTypeNode extends TypeNode {
    elementTypes: NodeArray<TypeNode>
  }

  interface UnionOrIntersectionTypeNode extends TypeNode {
    types: NodeArray<TypeNode>
  }

  // @kind(SyntaxKind.UnionType)
  interface UnionTypeNode extends UnionOrIntersectionTypeNode { }

  // @kind(SyntaxKind.IntersectionType)
  interface IntersectionTypeNode extends UnionOrIntersectionTypeNode { }

  // @kind(SyntaxKind.ParenthesizedType)
  interface ParenthesizedTypeNode extends TypeNode {
    type: TypeNode
  }

  // @kind(SyntaxKind.StringLiteralType)
  interface StringLiteralTypeNode extends LiteralLikeNode, TypeNode {
    _stringLiteralTypeBrand: any
  }

  // @kind(SyntaxKind.StringLiteral)
  interface StringLiteral extends LiteralExpression {
    _stringLiteralBrand: any
  }

  // Note: 'brands' in our syntax nodes serve to give us a small amount of nominal typing.
  // Consider 'Expression'.  Without the brand, 'Expression' is actually no different
  // (structurally) than 'Node'.  Because of this you can pass any Node to a def that
  // takes an Expression without any error.  By using the 'brands' we ensure that the type
  // checker actually thinks you have something of the right type.  Note: the brands are
  // never actually given values.  At runtime they have zero cost.

  interface Expression extends Node {
    _expressionBrand: any
    contextualType?: Type;  // Used to temporarily assign a contextual type during overload resolution
  }

  // @kind(SyntaxKind.OmittedExpression)
  interface OmittedExpression extends Expression { }

  interface UnaryExpression extends Expression {
    _unaryExpressionBrand: any
  }

  interface IncrementExpression extends UnaryExpression {
    _incrementExpressionBrand: any
  }

  // @kind(SyntaxKind.PrefixUnaryExpression)
  interface PrefixUnaryExpression extends IncrementExpression {
    operator: SyntaxKind
    operand: UnaryExpression
  }

  // @kind(SyntaxKind.PostfixUnaryExpression)
  interface PostfixUnaryExpression extends IncrementExpression {
    operand: LeftHandSideExpression
    operator: SyntaxKind
  }

  interface PostfixExpression extends UnaryExpression {
    _postfixExpressionBrand: any
  }

  interface LeftHandSideExpression extends IncrementExpression {
    _leftHandSideExpressionBrand: any
  }

  interface MemberExpression extends LeftHandSideExpression {
    _memberExpressionBrand: any
  }

  // @kind(SyntaxKind.TrueKeyword)
  // @kind(SyntaxKind.FalseKeyword)
  // @kind(SyntaxKind.NullKeyword)
  // @kind(SyntaxKind.ThisKeyword)
  // @kind(SyntaxKind.SuperKeyword)
  interface PrimaryExpression extends MemberExpression {
    _primaryExpressionBrand: any
  }

  // @kind(SyntaxKind.DeleteExpression)
  interface DeleteExpression extends UnaryExpression {
    expression: UnaryExpression
  }

  // @kind(SyntaxKind.TypeOfExpression)
  interface TypeOfExpression extends UnaryExpression {
    expression: UnaryExpression
  }

  // @kind(SyntaxKind.VoidExpression)
  interface VoidExpression extends UnaryExpression {
    expression: UnaryExpression
  }

  // @kind(SyntaxKind.AwaitExpression)
  interface AwaitExpression extends UnaryExpression {
    expression: UnaryExpression
  }

  // @kind(SyntaxKind.YieldExpression)
  interface YieldExpression extends Expression {
    asteriskToken?: Node
    expression?: Expression
  }

  // @kind(SyntaxKind.BinaryExpression)
  // Binary expressions can be declarations if they are 'exports.foo = bar' expressions in JS files
  interface BinaryExpression extends Expression, Declaration {
    left: Expression
    operatorToken: Node
    right: Expression
  }

  // @kind(SyntaxKind.ConditionalExpression)
  interface ConditionalExpression extends Expression {
    condition: Expression
    questionToken: Node
    whenTrue: Expression
    colonToken: Node
    whenFalse: Expression
  }

  type FunctionBody = Block
  type ConciseBody = FunctionBody | Expression

  // @kind(SyntaxKind.FunctionExpression)
  interface FunctionExpression extends PrimaryExpression, FunctionLikeDeclaration {
    name?: Identifier
    body: FunctionBody;  // Required, whereas the member inherited from FunctionDeclaration is optional
  }

  // @kind(SyntaxKind.ArrowFunction)
  interface ArrowFunction extends Expression, FunctionLikeDeclaration {
    equalsGreaterThanToken: Node
    body: ConciseBody
  }

  interface LiteralLikeNode extends Node {
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
  interface LiteralExpression extends LiteralLikeNode, PrimaryExpression {
    _literalExpressionBrand: any
  }

  // @kind(SyntaxKind.TemplateHead)
  // @kind(SyntaxKind.TemplateMiddle)
  // @kind(SyntaxKind.TemplateTail)
  interface TemplateLiteralFragment extends LiteralLikeNode {
    _templateLiteralFragmentBrand: any
  }

  // @kind(SyntaxKind.TemplateExpression)
  interface TemplateExpression extends PrimaryExpression {
    head: TemplateLiteralFragment
    templateSpans: NodeArray<TemplateSpan>
  }

  // Each of these corresponds to a substitution expression and a template literal, in that order.
  // The template literal must have kind TemplateMiddleLiteral or TemplateTailLiteral.
  // @kind(SyntaxKind.TemplateSpan)
  interface TemplateSpan extends Node {
    expression: Expression
    literal: TemplateLiteralFragment
  }

  // @kind(SyntaxKind.ParenthesizedExpression)
  interface ParenthesizedExpression extends PrimaryExpression {
    expression: Expression
  }

  // @kind(SyntaxKind.ArrayLiteralExpression)
  interface ArrayLiteralExpression extends PrimaryExpression {
    elements: NodeArray<Expression>
    /* @internal */
    multiLine?: Boolean
  }

  // @kind(SyntaxKind.SpreadElementExpression)
  interface SpreadElementExpression extends Expression {
    expression: Expression
  }

  // An ObjectLiteralExpression is the declaration node for an anonymous symbol.
  // @kind(SyntaxKind.ObjectLiteralExpression)
  interface ObjectLiteralExpression extends PrimaryExpression, Declaration {
    properties: NodeArray<ObjectLiteralElement>
    /* @internal */
    multiLine?: Boolean
  }

  // @kind(SyntaxKind.PropertyAccessExpression)
  interface PropertyAccessExpression extends MemberExpression, Declaration {
    expression: LeftHandSideExpression
    dotToken: Node
    name: Identifier
  }

  // @kind(SyntaxKind.ElementAccessExpression)
  interface ElementAccessExpression extends MemberExpression {
    expression: LeftHandSideExpression
    argumentExpression?: Expression
  }

  // @kind(SyntaxKind.CallExpression)
  interface CallExpression extends LeftHandSideExpression, Declaration {
    expression: LeftHandSideExpression
    typeArguments?: NodeArray<TypeNode>
    arguments: NodeArray<Expression>
  }

  // @kind(SyntaxKind.ExpressionWithTypeArguments)
  interface ExpressionWithTypeArguments extends TypeNode {
    expression: LeftHandSideExpression
    typeArguments?: NodeArray<TypeNode>
  }

  // @kind(SyntaxKind.NewExpression)
  interface NewExpression extends CallExpression, PrimaryExpression { }

  // @kind(SyntaxKind.TaggedTemplateExpression)
  interface TaggedTemplateExpression extends MemberExpression {
    tag: LeftHandSideExpression
    template: LiteralExpression | TemplateExpression
  }

  type CallLikeExpression = CallExpression | NewExpression | TaggedTemplateExpression | Decorator

  // @kind(SyntaxKind.AsExpression)
  interface AsExpression extends Expression {
    expression: Expression
    type: TypeNode
  }

  // @kind(SyntaxKind.TypeAssertionExpression)
  interface TypeAssertion extends UnaryExpression {
    type: TypeNode
    expression: UnaryExpression
  }

  type AssertionExpression = TypeAssertion | AsExpression

  /// A JSX expression of the form <TagName attrs>...</TagName>
  // @kind(SyntaxKind.JsxElement)
  interface JsxElement extends PrimaryExpression {
    openingElement: JsxOpeningElement
    children: NodeArray<JsxChild>
    closingElement: JsxClosingElement
  }

  /// The opening element of a <Tag>...</Tag> JsxElement
  // @kind(SyntaxKind.JsxOpeningElement)
  interface JsxOpeningElement extends Expression {
    _openingElementBrand?: any
    tagName: EntityName
    attributes: NodeArray<JsxAttribute | JsxSpreadAttribute>
  }

  /// A JSX expression of the form <TagName attrs />
  // @kind(SyntaxKind.JsxSelfClosingElement)
  interface JsxSelfClosingElement extends PrimaryExpression, JsxOpeningElement {
    _selfClosingElementBrand?: any
  }

  /// Either the opening tag in a <Tag>...</Tag> pair, or the lone <Tag /> in a self-closing form
  type JsxOpeningLikeElement = JsxSelfClosingElement | JsxOpeningElement

  // @kind(SyntaxKind.JsxAttribute)
  interface JsxAttribute extends Node {
    name: Identifier
    /// JSX attribute initializers are optional; <X y /> is sugar for <X y={true} />
    initializer?: Expression
  }

  // @kind(SyntaxKind.JsxSpreadAttribute)
  interface JsxSpreadAttribute extends Node {
    expression: Expression
  }

  // @kind(SyntaxKind.JsxClosingElement)
  interface JsxClosingElement extends Node {
    tagName: EntityName
  }

  // @kind(SyntaxKind.JsxExpression)
  interface JsxExpression extends Expression {
    expression?: Expression
  }

  // @kind(SyntaxKind.JsxText)
  interface JsxText extends Node {
    _jsxTextExpressionBrand: any
  }

  type JsxChild = JsxText | JsxExpression | JsxElement | JsxSelfClosingElement

  interface Statement extends Node {
    _statementBrand: any
  }

  // @kind(SyntaxKind.EmptyStatement)
  interface EmptyStatement extends Statement { }

  // @kind(SyntaxKind.DebuggerStatement)
  interface DebuggerStatement extends Statement { }

  // @kind(SyntaxKind.MissingDeclaration)
  interface MissingDeclaration extends DeclarationStatement, ClassElement, ObjectLiteralElement, TypeElement {
    name?: Identifier
  }

  type BlockLike = SourceFile | Block | ModuleBlock | CaseClause

  // @kind(SyntaxKind.Block)
  interface Block extends Statement {
    statements: NodeArray<Statement>
  }

  // @kind(SyntaxKind.VariableStatement)
  interface VariableStatement extends Statement {
    declarationList: VariableDeclarationList
  }

  // @kind(SyntaxKind.ExpressionStatement)
  interface ExpressionStatement extends Statement {
    expression: Expression
  }

  // @kind(SyntaxKind.IfStatement)
  interface IfStatement extends Statement {
    expression: Expression
    thenStatement: Statement
    elseStatement?: Statement
  }

  interface IterationStatement extends Statement {
    statement: Statement
  }

  // @kind(SyntaxKind.DoStatement)
  interface DoStatement extends IterationStatement {
    expression: Expression
  }

  // @kind(SyntaxKind.WhileStatement)
  interface WhileStatement extends IterationStatement {
    expression: Expression
  }

  // @kind(SyntaxKind.ForStatement)
  interface ForStatement extends IterationStatement {
    initializer?: VariableDeclarationList | Expression
    condition?: Expression
    incrementor?: Expression
  }

  // @kind(SyntaxKind.ForInStatement)
  interface ForInStatement extends IterationStatement {
    initializer: VariableDeclarationList | Expression
    expression: Expression
  }

  // @kind(SyntaxKind.ForOfStatement)
  interface ForOfStatement extends IterationStatement {
    initializer: VariableDeclarationList | Expression
    expression: Expression
  }

  // @kind(SyntaxKind.BreakStatement)
  interface BreakStatement extends Statement {
    label?: Identifier
  }

  // @kind(SyntaxKind.ContinueStatement)
  interface ContinueStatement extends Statement {
    label?: Identifier
  }

  type BreakOrContinueStatement = BreakStatement | ContinueStatement

  // @kind(SyntaxKind.ReturnStatement)
  interface ReturnStatement extends Statement {
    expression?: Expression
  }

  // @kind(SyntaxKind.WithStatement)
  interface WithStatement extends Statement {
    expression: Expression
    statement: Statement
  }

  // @kind(SyntaxKind.SwitchStatement)
  interface SwitchStatement extends Statement {
    expression: Expression
    caseBlock: CaseBlock
  }

  // @kind(SyntaxKind.CaseBlock)
  interface CaseBlock extends Node {
    clauses: NodeArray<CaseOrDefaultClause>
  }

  // @kind(SyntaxKind.CaseClause)
  interface CaseClause extends Node {
    expression: Expression
    statements: NodeArray<Statement>
  }

  // @kind(SyntaxKind.DefaultClause)
  interface DefaultClause extends Node {
    statements: NodeArray<Statement>
  }

  type CaseOrDefaultClause = CaseClause | DefaultClause

  // @kind(SyntaxKind.LabeledStatement)
  interface LabeledStatement extends Statement {
    label: Identifier
    statement: Statement
  }

  // @kind(SyntaxKind.ThrowStatement)
  interface ThrowStatement extends Statement {
    expression: Expression
  }

  // @kind(SyntaxKind.TryStatement)
  interface TryStatement extends Statement {
    tryBlock: Block
    catchClause?: CatchClause
    finallyBlock?: Block
  }

  // @kind(SyntaxKind.CatchClause)
  interface CatchClause extends Node {
    variableDeclaration: VariableDeclaration
    block: Block
  }

  interface ClassLikeDeclaration extends Declaration {
    name?: Identifier
    typeParameters?: NodeArray<TypeParameterDeclaration>
    heritageClauses?: NodeArray<HeritageClause>
    members: NodeArray<ClassElement>
  }

  // @kind(SyntaxKind.ClassDeclaration)
  interface ClassDeclaration extends ClassLikeDeclaration, DeclarationStatement {
    name?: Identifier
  }

  // @kind(SyntaxKind.ClassExpression)
  interface ClassExpression extends ClassLikeDeclaration, PrimaryExpression {
  }

  interface ClassElement extends Declaration {
    _classElementBrand: any
    name?: PropertyName
  }

  interface TypeElement extends Declaration {
    _typeElementBrand: any
    name?: PropertyName
    questionToken?: Node
  }

  // @kind(SyntaxKind.InterfaceDeclaration)
  interface InterfaceDeclaration extends DeclarationStatement {
    name: Identifier
    typeParameters?: NodeArray<TypeParameterDeclaration>
    heritageClauses?: NodeArray<HeritageClause>
    members: NodeArray<TypeElement>
  }

  // @kind(SyntaxKind.HeritageClause)
  interface HeritageClause extends Node {
    token: SyntaxKind
    types?: NodeArray<ExpressionWithTypeArguments>
  }

  // @kind(SyntaxKind.TypeAliasDeclaration)
  interface TypeAliasDeclaration extends DeclarationStatement {
    name: Identifier
    typeParameters?: NodeArray<TypeParameterDeclaration>
    type: TypeNode
  }

  // @kind(SyntaxKind.EnumMember)
  interface EnumMember extends Declaration {
    // This does include ComputedPropertyName, but the parser will give an error
    // if it parses a ComputedPropertyName in an EnumMember
    name: DeclarationName
    initializer?: Expression
  }

  // @kind(SyntaxKind.EnumDeclaration)
  interface EnumDeclaration extends DeclarationStatement {
    name: Identifier
    members: NodeArray<EnumMember>
  }

  type ModuleBody = ModuleBlock | ModuleDeclaration

  // @kind(SyntaxKind.ModuleDeclaration)
  interface ModuleDeclaration extends DeclarationStatement {
    name: Identifier | LiteralExpression
    body: ModuleBlock | ModuleDeclaration
  }

  // @kind(SyntaxKind.ModuleBlock)
  interface ModuleBlock extends Node, Statement {
    statements: NodeArray<Statement>
  }

  // @kind(SyntaxKind.ImportEqualsDeclaration)
  interface ImportEqualsDeclaration extends DeclarationStatement {
    name: Identifier

    // 'EntityName' for an internal module reference, 'ExternalModuleReference' for an external
    // module reference.
    moduleReference: EntityName | ExternalModuleReference
  }

  // @kind(SyntaxKind.ExternalModuleReference)
  interface ExternalModuleReference extends Node {
    expression?: Expression
  }

  // In case of:
  // import "mod"  => importClause = undefined, moduleSpecifier = "mod"
  // In rest of the cases, module specifier is String literal corresponding to module
  // ImportClause information is shown at its declaration below.
  // @kind(SyntaxKind.ImportDeclaration)
  interface ImportDeclaration extends Statement {
    importClause?: ImportClause
    moduleSpecifier: Expression
  }

  // In case of:
  // import d from "mod" => name = d, namedBinding = undefined
  // import * as ns from "mod" => name = undefined, namedBinding: NamespaceImport = { name: ns }
  // import d, * as ns from "mod" => name = d, namedBinding: NamespaceImport = { name: ns }
  // import { a, b as x } from "mod" => name = undefined, namedBinding: NamedImports = { elements: [{ name: a }, { name: x, propertyName: b}]}
  // import d, { a, b as x } from "mod" => name = d, namedBinding: NamedImports = { elements: [{ name: a }, { name: x, propertyName: b}]}
  // @kind(SyntaxKind.ImportClause)
  interface ImportClause extends Declaration {
    name?: Identifier; // Default binding
    namedBindings?: NamespaceImport | NamedImports
  }

  // @kind(SyntaxKind.NamespaceImport)
  interface NamespaceImport extends Declaration {
    name: Identifier
  }

  // @kind(SyntaxKind.ExportDeclaration)
  interface ExportDeclaration extends DeclarationStatement {
    exportClause?: NamedExports
    moduleSpecifier?: Expression
  }

  // @kind(SyntaxKind.NamedImports)
  interface NamedImports extends Node {
    elements: NodeArray<ImportSpecifier>
  }

  // @kind(SyntaxKind.NamedExports)
  interface NamedExports extends Node {
    elements: NodeArray<ExportSpecifier>
  }

  type NamedImportsOrExports = NamedImports | NamedExports

  // @kind(SyntaxKind.ImportSpecifier)
  interface ImportSpecifier extends Declaration {
    propertyName?: Identifier;  // Name preceding "as" keyword (or undefined when "as" is absent)
    name: Identifier;       // Declared name
  }

  // @kind(SyntaxKind.ExportSpecifier)
  interface ExportSpecifier extends Declaration {
    propertyName?: Identifier;  // Name preceding "as" keyword (or undefined when "as" is absent)
    name: Identifier;       // Declared name
  }

  type ImportOrExportSpecifier = ImportSpecifier | ExportSpecifier

  // @kind(SyntaxKind.ExportAssignment)
  interface ExportAssignment extends DeclarationStatement {
    isExportEquals?: Boolean
    expression: Expression
  }

  interface FileReference extends TextRange {
    fileName: String
  }

  interface CommentRange extends TextRange {
    hasTrailingNewLine?: Boolean
    kind: SyntaxKind
  }

  // represents a top level: { type } expression in a JSDoc comment.
  // @kind(SyntaxKind.JSDocTypeExpression)
  interface JSDocTypeExpression extends Node {
    type: JSDocType
  }

  interface JSDocType extends TypeNode {
    _jsDocTypeBrand: any
  }

  // @kind(SyntaxKind.JSDocAllType)
  interface JSDocAllType extends JSDocType {
    _JSDocAllTypeBrand: any
  }

  // @kind(SyntaxKind.JSDocUnknownType)
  interface JSDocUnknownType extends JSDocType {
    _JSDocUnknownTypeBrand: any
  }

  // @kind(SyntaxKind.JSDocArrayType)
  interface JSDocArrayType extends JSDocType {
    elementType: JSDocType
  }

  // @kind(SyntaxKind.JSDocUnionType)
  interface JSDocUnionType extends JSDocType {
    types: NodeArray<JSDocType>
  }

  // @kind(SyntaxKind.JSDocTupleType)
  interface JSDocTupleType extends JSDocType {
    types: NodeArray<JSDocType>
  }

  // @kind(SyntaxKind.JSDocNonNullableType)
  interface JSDocNonNullableType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocNullableType)
  interface JSDocNullableType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocRecordType)
  interface JSDocRecordType extends JSDocType, TypeLiteralNode {
    members: NodeArray<JSDocRecordMember>
  }

  // @kind(SyntaxKind.JSDocTypeReference)
  interface JSDocTypeReference extends JSDocType {
    name: EntityName
    typeArguments: NodeArray<JSDocType>
  }

  // @kind(SyntaxKind.JSDocOptionalType)
  interface JSDocOptionalType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocFunctionType)
  interface JSDocFunctionType extends JSDocType, SignatureDeclaration {
    parameters: NodeArray<ParameterDeclaration>
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocVariadicType)
  interface JSDocVariadicType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocConstructorType)
  interface JSDocConstructorType extends JSDocType {
    type: JSDocType
  }

  // @kind(SyntaxKind.JSDocThisType)
  interface JSDocThisType extends JSDocType {
    type: JSDocType
  }

  type JSDocTypeReferencingNode = JSDocThisType | JSDocConstructorType | JSDocVariadicType | JSDocOptionalType | JSDocNullableType | JSDocNonNullableType

  // @kind(SyntaxKind.JSDocRecordMember)
  interface JSDocRecordMember extends PropertySignature {
    name: Identifier | LiteralExpression
    type?: JSDocType
  }

  // @kind(SyntaxKind.JSDocComment)
  interface JSDocComment extends Node {
    tags: NodeArray<JSDocTag>
  }

  // @kind(SyntaxKind.JSDocTag)
  interface JSDocTag extends Node {
    atToken: Node
    tagName: Identifier
  }

  // @kind(SyntaxKind.JSDocTemplateTag)
  interface JSDocTemplateTag extends JSDocTag {
    typeParameters: NodeArray<TypeParameterDeclaration>
  }

  // @kind(SyntaxKind.JSDocReturnTag)
  interface JSDocReturnTag extends JSDocTag {
    typeExpression: JSDocTypeExpression
  }

  // @kind(SyntaxKind.JSDocTypeTag)
  interface JSDocTypeTag extends JSDocTag {
    typeExpression: JSDocTypeExpression
  }

  // @kind(SyntaxKind.JSDocParameterTag)
  interface JSDocParameterTag extends JSDocTag {
    preParameterName?: Identifier
    typeExpression?: JSDocTypeExpression
    postParameterName?: Identifier
    isBracketed: Boolean
  }

  interface AmdDependency {
    path: String
    name: String
  }

  // Source files are declarations when they are external modules.
  // @kind(SyntaxKind.SourceFile)
  interface SourceFile extends Declaration {
    statements: NodeArray<Statement>
    endOfFileToken: Node

    fileName: String
    /* internal */ path: Path
    text: String

    amdDependencies: AmdDependency[]
    moduleName: String
    referencedFiles: FileReference[]
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
    /* @internal */ parseDiagnostics: Diagnostic[]

    // File level diagnostics reported by the binder.
    /* @internal */ bindDiagnostics: Diagnostic[]

    // Stores a line map for the file.
    // This field should never be used directly to obtain line map, use getLineMap def instead.
    /* @internal */ lineMap: Int[]
    /* @internal */ classifiableNames?: Map<String>
    // Stores a mapping 'external module reference text' -> 'resolved file name' | undefined
    // It is used to resolve module names in the checker.
    // Content of this field should never be used directly - use getResolvedModuleFileName/setResolvedModuleFileName functions instead
    /* @internal */ resolvedModules: Map<ResolvedModule>
    /* @internal */ imports: LiteralExpression[]
    /* @internal */ moduleAugmentations: LiteralExpression[]
  }

  interface ScriptReferenceHost {
    getCompilerOptions(): CompilerOptions
    getSourceFile(fileName: String): SourceFile
    getCurrentDirectory(): String
  }

  interface ParseConfigHost {
    readDirectory(rootDir: String, extension: String, exclude: String[]): String[]
  }

  interface WriteFileCallback {
    (fileName: String, data: String, writeByteOrderMark: Boolean, onError?: (message: String) => void): void
  }

  class OperationCanceledException { }

  interface CancellationToken {
    isCancellationRequested(): Boolean

    /** @throws OperationCanceledException if isCancellationRequested is true */
    throwIfCancellationRequested(): void
  }

  interface Program extends ScriptReferenceHost {

    /**
     * Get a list of root file names that were passed to a 'createProgram'
     */
    getRootFileNames(): String[]

    /**
     * Get a list of files in the program
     */
    getSourceFiles(): SourceFile[]

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

    getOptionsDiagnostics(cancellationToken?: CancellationToken): Diagnostic[]
    getGlobalDiagnostics(cancellationToken?: CancellationToken): Diagnostic[]
    getSyntacticDiagnostics(sourceFile?: SourceFile, cancellationToken?: CancellationToken): Diagnostic[]
    getSemanticDiagnostics(sourceFile?: SourceFile, cancellationToken?: CancellationToken): Diagnostic[]
    getDeclarationDiagnostics(sourceFile?: SourceFile, cancellationToken?: CancellationToken): Diagnostic[]

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

  interface SourceMapSpan {
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

  interface SourceMapData {
    sourceMapFilePath: String;       // Where the sourcemap file is written
    jsSourceMappingURL: String;      // source map URL written in the .js file
    sourceMapFile: String;         // Source map's file field - .js file name
    sourceMapSourceRoot: String;     // Source map's sourceRoot field - location where the sources will be present if not ""
    sourceMapSources: String[];      // Source map's sources field - list of sources that can be indexed in this source map
    sourceMapSourcesContent?: String[];  // Source map's sourcesContent field - list of the sources' text to be embedded in the source map
    inputSourceFileNames: String[];    // Input source file (which one can use on program to get the file), 1:1 mapping with the sourceMapSources list
    sourceMapNames?: String[];       // Source map's names field - list of names that can be indexed in this source map
    sourceMapMappings: String;       // Source map's mapping field - encoded source map spans
    sourceMapDecodedMappings: SourceMapSpan[];  // Raw source map spans that were encoded into the sourceMapMappings
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

  interface EmitResult {
    emitSkipped: Boolean
    diagnostics: Diagnostic[]
    /* @internal */ sourceMaps: SourceMapData[];  // Array of sourceMapData if compiler emitted sourcemaps
  }

  /* @internal */
  interface TypeCheckerHost {
    getCompilerOptions(): CompilerOptions

    getSourceFiles(): SourceFile[]
    getSourceFile(fileName: String): SourceFile
  }

  interface TypeChecker {
    getTypeOfSymbolAtLocation(symbol: Symbol, node: Node): Type
    getDeclaredTypeOfSymbol(symbol: Symbol): Type
    getPropertiesOfType(type: Type): Symbol[]
    getPropertyOfType(type: Type, propertyName: String): Symbol
    getSignaturesOfType(type: Type, kind: SignatureKind): Signature[]
    getIndexTypeOfType(type: Type, kind: IndexKind): Type
    getBaseTypes(type: InterfaceType): ObjectType[]
    getReturnTypeOfSignature(signature: Signature): Type

    getSymbolsInScope(location: Node, meaning: SymbolFlags): Symbol[]
    getSymbolAtLocation(node: Node): Symbol
    getSymbolsOfParameterPropertyDeclaration(parameter: ParameterDeclaration, parameterName: String): Symbol[]
    getShorthandAssignmentValueSymbol(location: Node): Symbol
    getExportSpecifierLocalTargetSymbol(location: ExportSpecifier): Symbol
    getTypeAtLocation(node: Node): Type
    typeToString(type: Type, enclosingDeclaration?: Node, flags?: TypeFormatFlags): String
    symbolToString(symbol: Symbol, enclosingDeclaration?: Node, meaning?: SymbolFlags): String
    getSymbolDisplayBuilder(): SymbolDisplayBuilder
    getFullyQualifiedName(symbol: Symbol): String
    getAugmentedPropertiesOfType(type: Type): Symbol[]
    getRootSymbols(symbol: Symbol): Symbol[]
    getContextualType(node: Expression): Type
    getResolvedSignature(node: CallLikeExpression, candidatesOutArray?: Signature[]): Signature
    getSignatureFromDeclaration(declaration: SignatureDeclaration): Signature
    isImplementationOfOverload(node: FunctionLikeDeclaration): Boolean
    isUndefinedSymbol(symbol: Symbol): Boolean
    isArgumentsSymbol(symbol: Symbol): Boolean
    isUnknownSymbol(symbol: Symbol): Boolean

    getConstantValue(node: EnumMember | PropertyAccessExpression | ElementAccessExpression): Int
    isValidPropertyAccess(node: PropertyAccessExpression | QualifiedName, propertyName: String): Boolean
    getAliasedSymbol(symbol: Symbol): Symbol
    getExportsOfModule(moduleSymbol: Symbol): Symbol[]

    getJsxElementAttributesType(elementNode: JsxOpeningLikeElement): Type
    getJsxIntrinsicTagNames(): Symbol[]
    isOptionalParameter(node: ParameterDeclaration): Boolean

    // Should not be called directly.  Should only be accessed through the Program instance.
    /* @internal */ getDiagnostics(sourceFile?: SourceFile, cancellationToken?: CancellationToken): Diagnostic[]
    /* @internal */ getGlobalDiagnostics(): Diagnostic[]
    /* @internal */ getEmitResolver(sourceFile?: SourceFile, cancellationToken?: CancellationToken): EmitResolver

    /* @internal */ getNodeCount(): Int
    /* @internal */ getIdentifierCount(): Int
    /* @internal */ getSymbolCount(): Int
    /* @internal */ getTypeCount(): Int
  }

  interface SymbolDisplayBuilder {
    buildTypeDisplay(type: Type, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): void
    buildSymbolDisplay(symbol: Symbol, writer: SymbolWriter, enclosingDeclaration?: Node, meaning?: SymbolFlags, flags?: SymbolFormatFlags): void
    buildSignatureDisplay(signatures: Signature, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags, kind?: SignatureKind): void
    buildParameterDisplay(parameter: Symbol, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): void
    buildTypeParameterDisplay(tp: TypeParameter, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): void
    buildTypeParameterDisplayFromSymbol(symbol: Symbol, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): void
    buildDisplayForParametersAndDelimiters(parameters: Symbol[], writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): void
    buildDisplayForTypeParametersAndDelimiters(typeParameters: TypeParameter[], writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): void
    buildReturnTypeDisplay(signature: Signature, writer: SymbolWriter, enclosingDeclaration?: Node, flags?: TypeFormatFlags): void
  }

  interface SymbolWriter {
    writeKeyword(text: String): void
    writeOperator(text: String): void
    writePunctuation(text: String): void
    writeSpace(text: String): void
    writeStringLiteral(text: String): void
    writeParameter(text: String): void
    writeSymbol(text: String, symbol: Symbol): void
    writeLine(): void
    increaseIndent(): void
    decreaseIndent(): void
    clear(): void

    // Called when the symbol writer encounters a symbol to write.  Currently only used by the
    // declaration emitter to help determine if it should patch up the final declaration file
    // with import statements it previously saw (but chose not to emit).
    trackSymbol(symbol: Symbol, enclosingDeclaration?: Node, meaning?: SymbolFlags): void
    reportInaccessibleThisError(): void
  }

  val enum TypeFormatFlags {
    None              = 0x00000000,
    WriteArrayAsGenericType     = 0x00000001,  // Write Array<T> instead T[]
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

  interface TypePredicate {
    kind: TypePredicateKind
    type: Type
  }

  // @kind (TypePredicateKind.This)
  interface ThisTypePredicate extends TypePredicate {
    _thisTypePredicateBrand: any
  }

  // @kind (TypePredicateKind.Identifier)
  interface IdentifierTypePredicate extends TypePredicate {
    parameterName: String
    parameterIndex: Int
  }

  /* @internal */
  type AnyImportSyntax = ImportDeclaration | ImportEqualsDeclaration

  /* @internal */
  interface SymbolVisibilityResult {
    accessibility: SymbolAccessibility
    aliasesToMakeVisible?: AnyImportSyntax[]; // aliases that need to have this symbol visible
    errorSymbolName?: String; // Optional symbol name that results in error
    errorNode?: Node; // optional node that results in error
  }

  /* @internal */
  interface SymbolAccessibilityResult extends SymbolVisibilityResult {
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
  interface EmitResolver {
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
    collectLinkedAliases(node: Identifier): Node[]
    isImplementationOfOverload(node: FunctionLikeDeclaration): Boolean
    writeTypeOfDeclaration(declaration: AccessorDeclaration | VariableLikeDeclaration, enclosingDeclaration: Node, flags: TypeFormatFlags, writer: SymbolWriter): void
    writeReturnTypeOfSignatureDeclaration(signatureDeclaration: SignatureDeclaration, enclosingDeclaration: Node, flags: TypeFormatFlags, writer: SymbolWriter): void
    writeTypeOfExpression(expr: Expression, enclosingDeclaration: Node, flags: TypeFormatFlags, writer: SymbolWriter): void
    isSymbolAccessible(symbol: Symbol, enclosingDeclaration: Node, meaning: SymbolFlags): SymbolAccessibilityResult
    isEntityNameVisible(entityName: EntityName | Expression, enclosingDeclaration: Node): SymbolVisibilityResult
    // Returns the constant value this property access resolves to, or 'undefined' for a non-constant
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
    ClassExcludes = (Value | Type) & ~(ValueModule | Interface), // class-interface mergability done in checker.ts
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

  interface Symbol {
    flags: SymbolFlags;           // Symbol flags
    name: String;               // Name of symbol
    declarations?: Declaration[];       // Declarations associated with this symbol
    valueDeclaration?: Declaration;     // First value declaration of the symbol

    members?: SymbolTable;          // Class, interface or literal instance members
    exports?: SymbolTable;          // Module exports
    /* @internal */ id?: Int;      // Unique id (used to look up SymbolLinks)
    /* @internal */ mergeId?: Int;     // Merge id (used to look up merged symbol)
    /* @internal */ parent?: Symbol;    // Parent symbol
    /* @internal */ exportSymbol?: Symbol;  // Exported symbol associated with this symbol
    /* @internal */ constEnumOnlyModule?: Boolean; // True if module contains only val enums or other modules with only val enums
  }

  /* @internal */
  interface SymbolLinks {
    target?: Symbol;          // Resolved (non-alias) target of an alias
    type?: Type;            // Type of value symbol
    declaredType?: Type;        // Type of class, interface, enum, type alias, or type parameter
    typeParameters?: TypeParameter[];   // Type parameters of type alias (undefined if non-generic)
    inferredClassType?: Type;       // Type of an inferred ES5 class
    instantiations?: Map<Type>;     // Instantiations of generic type alias (undefined if non-generic)
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
  interface TransientSymbol extends Symbol, SymbolLinks { }

  interface SymbolTable {
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
  interface NodeLinks {
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
  interface Type {
    flags: TypeFlags;        // Flags
    /* @internal */ id: Int;    // Unique ID
    symbol?: Symbol;         // Symbol associated with type (if any)
    pattern?: DestructuringPattern;  // Destructuring pattern represented by type (if any)
  }

  /* @internal */
  // Intrinsic types (TypeFlags.Intrinsic)
  interface IntrinsicType extends Type {
    intrinsicName: String;  // Name of intrinsic type
  }

  // Predicate types (TypeFlags.Predicate)
  interface PredicateType extends Type {
    predicate: ThisTypePredicate | IdentifierTypePredicate
  }

  // String literal types (TypeFlags.StringLiteral)
  interface StringLiteralType extends Type {
    text: String;  // Text of String literal
  }

  // Object types (TypeFlags.ObjectType)
  interface ObjectType extends Type { }

  // Class and interface types (TypeFlags.Class and TypeFlags.Interface)
  interface InterfaceType extends ObjectType {
    typeParameters: TypeParameter[];       // Type parameters (undefined if non-generic)
    outerTypeParameters: TypeParameter[];    // Outer type parameters (undefined if none)
    localTypeParameters: TypeParameter[];    // Local type parameters (undefined if none)
    thisType: TypeParameter;           // The "this" type (undefined if none)
    /* @internal */
    resolvedBaseConstructorType?: Type;    // Resolved base constructor type of class
    /* @internal */
    resolvedBaseTypes: ObjectType[];       // Resolved base types
  }

  interface InterfaceTypeWithDeclaredMembers extends InterfaceType {
    declaredProperties: Symbol[];        // Declared members
    declaredCallSignatures: Signature[];     // Declared call signatures
    declaredConstructSignatures: Signature[];  // Declared construct signatures
    declaredStringIndexInfo: IndexInfo;    // Declared String indexing info
    declaredNumberIndexInfo: IndexInfo;    // Declared numeric indexing info
  }

  // Type references (TypeFlags.Reference). When a class or interface has type parameters or
  // a "this" type, references to the class or interface are made using type references. The
  // typeArguments property specifies the types to substitute for the type parameters of the
  // class or interface and optionally includes an extra element that specifies the type to
  // substitute for "this" in the resulting instantiation. When no extra argument is present,
  // the type reference itself is substituted for "this". The typeArguments property is undefined
  // if the class or interface has no type parameters and the reference isn't specifying an
  // explicit "this" argument.
  interface TypeReference extends ObjectType {
    target: GenericType;  // Type reference target
    typeArguments: Type[];  // Type reference type arguments (undefined if none)
  }

  // Generic class and interface types
  interface GenericType extends InterfaceType, TypeReference {
    /* @internal */
    instantiations: Map<TypeReference>;   // Generic instantiation cache
  }

  interface TupleType extends ObjectType {
    elementTypes: Type[];  // Element types
  }

  interface UnionOrIntersectionType extends Type {
    types: Type[];          // Constituent types
    /* @internal */
    reducedType: Type;        // Reduced union type (all subtypes removed)
    /* @internal */
    resolvedProperties: SymbolTable;  // Cache of resolved properties
  }

  interface UnionType extends UnionOrIntersectionType { }

  interface IntersectionType extends UnionOrIntersectionType { }

  /* @internal */
  // An instantiated anonymous type has a target and a mapper
  interface AnonymousType extends ObjectType {
    target?: AnonymousType;  // Instantiation target
    mapper?: TypeMapper;   // Instantiation mapper
  }

  /* @internal */
  // Resolved object, union, or intersection type
  interface ResolvedType extends ObjectType, UnionOrIntersectionType {
    members: SymbolTable;        // Properties by name
    properties: Symbol[];        // Properties
    callSignatures: Signature[];     // Call signatures of type
    constructSignatures: Signature[];  // Construct signatures of type
    stringIndexInfo?: IndexInfo;     // String indexing info
    numberIndexInfo?: IndexInfo;     // Numeric indexing info
  }

  /* @internal */
  // Object literals are initially marked fresh. Freshness disappears following an assignment,
  // before a type assertion, or when when an object literal's type is widened. The regular
  // version of a fresh type is identical except for the TypeFlags.FreshObjectLiteral flag.
  interface FreshObjectLiteralType extends ResolvedType {
    regularType: ResolvedType;  // Regular version of fresh type
  }

  // Just a place to cache element types of iterables and iterators
  /* @internal */
  interface IterableOrIteratorType extends ObjectType, UnionType {
    iterableElementType?: Type
    iteratorElementType?: Type
  }

  // Type parameters (TypeFlags.TypeParameter)
  interface TypeParameter extends Type {
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

  interface Signature {
    declaration: SignatureDeclaration;  // Originating declaration
    typeParameters: TypeParameter[];  // Type parameters (undefined if non-generic)
    parameters: Symbol[];         // Parameters
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
    unionSignatures?: Signature[];    // Underlying signatures of a union signature
    /* @internal */
    erasedSignatureCache?: Signature;   // Erased version of signature (deferred)
    /* @internal */
    isolatedSignatureType?: ObjectType; // A manufactured type that just contains the signature for purposes of signature comparison
  }

  val enum IndexKind {
    String,
    Number,
  }

  interface IndexInfo {
    type: Type
    isReadonly: Boolean
    declaration?: SignatureDeclaration
  }

  /* @internal */
  interface TypeMapper {
    (t: TypeParameter): Type
    instantiations?: Type[];  // Cache of instantiations created using this type mapper.
    context?: InferenceContext; // The inference context this mapper was created from.
                  // Only inference mappers have this set (in createInferenceMapper).
                  // The identity mapper and regular instantiation mappers do not need it.
  }

  /* @internal */
  interface TypeInferences {
    primary: Type[];  // Inferences made directly to a type parameter
    secondary: Type[];  // Inferences made to a type parameter in a union type
    isFixed: Boolean;   // Whether the type parameter is fixed, as defined in section 4.12.2 of the TypeScript spec
              // If a type parameter is fixed, no more inferences can be made for the type parameter
  }

  /* @internal */
  interface InferenceContext {
    typeParameters: TypeParameter[];  // Type parameters for which inferences are made
    inferUnionTypes: Boolean;       // Infer union types for disjoint candidates (otherwise undefinedType)
    inferences: TypeInferences[];     // Inferences made for each type parameter
    inferredTypes: Type[];        // Inferred type for each type parameter
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

  interface DiagnosticMessage {
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
  interface DiagnosticMessageChain {
    messageText: String
    category: DiagnosticCategory
    code: Int
    next?: DiagnosticMessageChain
  }

  interface Diagnostic {
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

  type RootPaths = String[]
  type PathSubstitutions = Map<String[]>
  type TsConfigOnlyOptions = RootPaths | PathSubstitutions

  interface CompilerOptions {
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

  interface LineAndCharacter {
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

  interface ParsedCommandLine {
    options: CompilerOptions
    fileNames: String[]
    errors: Diagnostic[]
  }

  /* @internal */
  interface CommandLineOptionBase {
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
  interface CommandLineOptionOfPrimitiveType extends CommandLineOptionBase {
    type: "String" | "Int" | "Boolean"
  }

  /* @internal */
  interface CommandLineOptionOfCustomType extends CommandLineOptionBase {
    type: Map<Int>;       // an object literal mapping named values to actual values
    error: DiagnosticMessage;    // The error given when the argument does not fit a customized 'type'
  }

  /* @internal */
  interface TsConfigOnlyOption extends CommandLineOptionBase {
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

  interface ModuleResolutionHost {
    fileExists(fileName: String): Boolean
    // readFile def is used to read arbitrary text files on disk, i.e. when resolution procedure needs the content of 'package.json'
    // to determine location of bundled typings for node module
    readFile(fileName: String): String
    trace?(s: String): void
    directoryExists?(directoryName: String): Boolean
  }

  interface ResolvedModule {
    resolvedFileName: String
    /*
     * Denotes if 'resolvedFileName' is isExternalLibraryImport and thus should be proper external module:
     * - be a .d.ts file
     * - use top level imports\exports
     * - don't use tripleslash references
     */
    isExternalLibraryImport?: Boolean
  }

  interface ResolvedModuleWithFailedLookupLocations {
    resolvedModule: ResolvedModule
    failedLookupLocations: String[]
  }

  interface CompilerHost extends ModuleResolutionHost {
    getSourceFile(fileName: String, languageVersion: ScriptTarget, onError?: (message: String) => void): SourceFile
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
    resolveModuleNames?(moduleNames: String[], containingFile: String): ResolvedModule[]
  }

  interface TextSpan {
    start: Int
    length: Int
  }

  interface TextChangeRange {
    span: TextSpan
    newLength: Int
  }

  /* @internal */
  interface DiagnosticCollection {
    // Adds a diagnostic to this diagnostic collection.
    add(diagnostic: Diagnostic): void

    // Gets all the diagnostics that aren't associated with a file.
    getGlobalDiagnostics(): Diagnostic[]

    // If fileName is provided, gets all the diagnostics associated with that file name.
    // Otherwise, returns all the diagnostics (global and file associated) in this collection.
    getDiagnostics(fileName?: String): Diagnostic[]

    // Gets a count of how many times this collection has been modified.  This value changes
    // each time 'add' is called (regardless of whether or not an equivalent diagnostic was
    // already in the collection).  As such, it can be used as a simple way to tell if any
    // operation caused diagnostics to be returned by storing and comparing the return value
    // of this method before/after the operation is performed.
    getModificationCount(): Int

    /* @internal */ reattachFileDiagnostics(newFile: SourceFile): void
  }
}
