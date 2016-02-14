package be.doeraene.tsc

/// <reference path="checker.ts"/>
/// <reference path="sourcemap.ts" />
/// <reference path="declarationEmitter.ts"/>

/* @internal */
object Emitter {
  def getResolvedExternalModuleName(host: EmitHost, file: SourceFile): String = {
    return file.moduleName || getExternalModuleNameFromPath(host, file.fileName)
  }

  def getExternalModuleNameFromDeclaration(host: EmitHost, resolver: EmitResolver, declaration: ImportEqualsDeclaration | ImportDeclaration | ExportDeclaration): String = {
    val file = resolver.getExternalModuleFileFromDeclaration(declaration)
    if (!file || isDeclarationFile(file)) {
      return ()
    }
    return getResolvedExternalModuleName(host, file)
  }

  type DependencyGroup = Array<ImportDeclaration | ImportEqualsDeclaration | ExportDeclaration>

  val enum Jump {
    Break     = 1 << 1,
    Continue  = 1 << 2,
    Return    = 1 << 3
  }

  val entities: Map<Int> = {
    "quot": 0x0022,
    "amp": 0x0026,
    "apos": 0x0027,
    "lt": 0x003C,
    "gt": 0x003E,
    "nbsp": 0x00A0,
    "iexcl": 0x00A1,
    "cent": 0x00A2,
    "pound": 0x00A3,
    "curren": 0x00A4,
    "yen": 0x00A5,
    "brvbar": 0x00A6,
    "sect": 0x00A7,
    "uml": 0x00A8,
    "copy": 0x00A9,
    "ordf": 0x00AA,
    "laquo": 0x00AB,
    "not": 0x00AC,
    "shy": 0x00AD,
    "reg": 0x00AE,
    "macr": 0x00AF,
    "deg": 0x00B0,
    "plusmn": 0x00B1,
    "sup2": 0x00B2,
    "sup3": 0x00B3,
    "acute": 0x00B4,
    "micro": 0x00B5,
    "para": 0x00B6,
    "middot": 0x00B7,
    "cedil": 0x00B8,
    "sup1": 0x00B9,
    "ordm": 0x00BA,
    "raquo": 0x00BB,
    "frac14": 0x00BC,
    "frac12": 0x00BD,
    "frac34": 0x00BE,
    "iquest": 0x00BF,
    "Agrave": 0x00C0,
    "Aacute": 0x00C1,
    "Acirc": 0x00C2,
    "Atilde": 0x00C3,
    "Auml": 0x00C4,
    "Aring": 0x00C5,
    "AElig": 0x00C6,
    "Ccedil": 0x00C7,
    "Egrave": 0x00C8,
    "Eacute": 0x00C9,
    "Ecirc": 0x00CA,
    "Euml": 0x00CB,
    "Igrave": 0x00CC,
    "Iacute": 0x00CD,
    "Icirc": 0x00CE,
    "Iuml": 0x00CF,
    "ETH": 0x00D0,
    "Ntilde": 0x00D1,
    "Ograve": 0x00D2,
    "Oacute": 0x00D3,
    "Ocirc": 0x00D4,
    "Otilde": 0x00D5,
    "Ouml": 0x00D6,
    "times": 0x00D7,
    "Oslash": 0x00D8,
    "Ugrave": 0x00D9,
    "Uacute": 0x00DA,
    "Ucirc": 0x00DB,
    "Uuml": 0x00DC,
    "Yacute": 0x00DD,
    "THORN": 0x00DE,
    "szlig": 0x00DF,
    "agrave": 0x00E0,
    "aacute": 0x00E1,
    "acirc": 0x00E2,
    "atilde": 0x00E3,
    "auml": 0x00E4,
    "aring": 0x00E5,
    "aelig": 0x00E6,
    "ccedil": 0x00E7,
    "egrave": 0x00E8,
    "eacute": 0x00E9,
    "ecirc": 0x00EA,
    "euml": 0x00EB,
    "igrave": 0x00EC,
    "iacute": 0x00ED,
    "icirc": 0x00EE,
    "iuml": 0x00EF,
    "eth": 0x00F0,
    "ntilde": 0x00F1,
    "ograve": 0x00F2,
    "oacute": 0x00F3,
    "ocirc": 0x00F4,
    "otilde": 0x00F5,
    "ouml": 0x00F6,
    "divide": 0x00F7,
    "oslash": 0x00F8,
    "ugrave": 0x00F9,
    "uacute": 0x00FA,
    "ucirc": 0x00FB,
    "uuml": 0x00FC,
    "yacute": 0x00FD,
    "thorn": 0x00FE,
    "yuml": 0x00FF,
    "OElig": 0x0152,
    "oelig": 0x0153,
    "Scaron": 0x0160,
    "scaron": 0x0161,
    "Yuml": 0x0178,
    "fnof": 0x0192,
    "circ": 0x02C6,
    "tilde": 0x02DC,
    "Alpha": 0x0391,
    "Beta": 0x0392,
    "Gamma": 0x0393,
    "Delta": 0x0394,
    "Epsilon": 0x0395,
    "Zeta": 0x0396,
    "Eta": 0x0397,
    "Theta": 0x0398,
    "Iota": 0x0399,
    "Kappa": 0x039A,
    "Lambda": 0x039B,
    "Mu": 0x039C,
    "Nu": 0x039D,
    "Xi": 0x039E,
    "Omicron": 0x039F,
    "Pi": 0x03A0,
    "Rho": 0x03A1,
    "Sigma": 0x03A3,
    "Tau": 0x03A4,
    "Upsilon": 0x03A5,
    "Phi": 0x03A6,
    "Chi": 0x03A7,
    "Psi": 0x03A8,
    "Omega": 0x03A9,
    "alpha": 0x03B1,
    "beta": 0x03B2,
    "gamma": 0x03B3,
    "delta": 0x03B4,
    "epsilon": 0x03B5,
    "zeta": 0x03B6,
    "eta": 0x03B7,
    "theta": 0x03B8,
    "iota": 0x03B9,
    "kappa": 0x03BA,
    "lambda": 0x03BB,
    "mu": 0x03BC,
    "nu": 0x03BD,
    "xi": 0x03BE,
    "omicron": 0x03BF,
    "pi": 0x03C0,
    "rho": 0x03C1,
    "sigmaf": 0x03C2,
    "sigma": 0x03C3,
    "tau": 0x03C4,
    "upsilon": 0x03C5,
    "phi": 0x03C6,
    "chi": 0x03C7,
    "psi": 0x03C8,
    "omega": 0x03C9,
    "thetasym": 0x03D1,
    "upsih": 0x03D2,
    "piv": 0x03D6,
    "ensp": 0x2002,
    "emsp": 0x2003,
    "thinsp": 0x2009,
    "zwnj": 0x200C,
    "zwj": 0x200D,
    "lrm": 0x200E,
    "rlm": 0x200F,
    "ndash": 0x2013,
    "mdash": 0x2014,
    "lsquo": 0x2018,
    "rsquo": 0x2019,
    "sbquo": 0x201A,
    "ldquo": 0x201C,
    "rdquo": 0x201D,
    "bdquo": 0x201E,
    "dagger": 0x2020,
    "Dagger": 0x2021,
    "bull": 0x2022,
    "hellip": 0x2026,
    "permil": 0x2030,
    "prime": 0x2032,
    "Prime": 0x2033,
    "lsaquo": 0x2039,
    "rsaquo": 0x203A,
    "oline": 0x203E,
    "frasl": 0x2044,
    "euro": 0x20AC,
    "image": 0x2111,
    "weierp": 0x2118,
    "real": 0x211C,
    "trade": 0x2122,
    "alefsym": 0x2135,
    "larr": 0x2190,
    "uarr": 0x2191,
    "rarr": 0x2192,
    "darr": 0x2193,
    "harr": 0x2194,
    "crarr": 0x21B5,
    "lArr": 0x21D0,
    "uArr": 0x21D1,
    "rArr": 0x21D2,
    "dArr": 0x21D3,
    "hArr": 0x21D4,
    "forall": 0x2200,
    "part": 0x2202,
    "exist": 0x2203,
    "empty": 0x2205,
    "nabla": 0x2207,
    "isin": 0x2208,
    "notin": 0x2209,
    "ni": 0x220B,
    "prod": 0x220F,
    "sum": 0x2211,
    "minus": 0x2212,
    "lowast": 0x2217,
    "radic": 0x221A,
    "prop": 0x221D,
    "infin": 0x221E,
    "ang": 0x2220,
    "and": 0x2227,
    "or": 0x2228,
    "cap": 0x2229,
    "cup": 0x222A,
    "int": 0x222B,
    "there4": 0x2234,
    "sim": 0x223C,
    "cong": 0x2245,
    "asymp": 0x2248,
    "ne": 0x2260,
    "equiv": 0x2261,
    "le": 0x2264,
    "ge": 0x2265,
    "sub": 0x2282,
    "sup": 0x2283,
    "nsub": 0x2284,
    "sube": 0x2286,
    "supe": 0x2287,
    "oplus": 0x2295,
    "otimes": 0x2297,
    "perp": 0x22A5,
    "sdot": 0x22C5,
    "lceil": 0x2308,
    "rceil": 0x2309,
    "lfloor": 0x230A,
    "rfloor": 0x230B,
    "lang": 0x2329,
    "rang": 0x232A,
    "loz": 0x25CA,
    "spades": 0x2660,
    "clubs": 0x2663,
    "hearts": 0x2665,
    "diams": 0x2666
  }

  // Flags enum to track count of temp variables and a few dedicated names
  val enum TempFlags {
    Auto    = 0x00000000,  // No preferred name
    CountMask = 0x0FFFFFFF,  // Temp variable counter
    _i    = 0x10000000,  // Use/preference flag for '_i'
  }

  val enum CopyDirection {
    ToOriginal,
    ToOutParameter
  }

  /**
   * If loop contains block scoped binding captured in some def then loop body is converted to a def.
   * Lexical bindings declared in loop initializer will be passed into the loop body def as parameters,
   * however if this binding is modified inside the body - this new value should be propagated back to the original binding.
   * This is done by declaring new variable (out parameter holder) outside of the loop for every binding that is reassigned inside the body.
   * On every iteration this variable is initialized with value of corresponding binding.
   * At every point where control flow leaves the loop either explicitly (break/continue) or implicitly (at the end of loop body)
   * we copy the value inside the loop to the out parameter holder.
   *
   * for (var x;;) {
   *   var a = 1
   *   var b = () => a
   *   x++
   *   if (...) break
   *   ...
   * }
   *
   * will be converted to
   *
   * var out_x
   * var loop = def(x) {
   *   var a = 1
   *   var b = def() { return a; }
   *   x++
   *   if (...) return out_x = x, "break"
   *   ...
   *   out_x = x
   * }
   * for (var x;;) {
   *   out_x = x
   *   var state = loop(x)
   *   x = out_x
   *   if (state == "break") break
   * }
   *
   * NOTE: values to out parameters are not copies if loop is abrupted with 'return' - in this case this will end the entire enclosing def
   * so nobody can observe this new value.
   */
  trait LoopOutParameter {
    originalName: Identifier
    outParamName: String
  }

  // targetSourceFile is when users only want one file in entire project to be emitted. This is used in compileOnSave feature
  def emitFiles(resolver: EmitResolver, host: EmitHost, targetSourceFile: SourceFile): EmitResult = {
    // emit output for the __extends helper def
    val extendsHelper = `
var __extends = (this && this.__extends) || def (d, b) {
  for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p]
  def __() { this.constructor = d; }
  d.prototype = b == null ? Object.create(b) : (__.prototype = b.prototype, new __())
};`

    // emit output for the __decorate helper def
    val decorateHelper = `
var __decorate = (this && this.__decorate) || def (decorators, target, key, desc) {
  var c = arguments.length, r = c < 3 ? target : desc == null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d
  if (typeof Reflect == "object" && typeof Reflect.decorate == "def") r = Reflect.decorate(decorators, target, key, desc)
  else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r
  return c > 3 && r && Object.defineProperty(target, key, r), r
};`

    // emit output for the __metadata helper def
    val metadataHelper = `
var __metadata = (this && this.__metadata) || def (k, v) {
  if (typeof Reflect == "object" && typeof Reflect.metadata == "def") return Reflect.metadata(k, v)
};`

    // emit output for the __param helper def
    val paramHelper = `
var __param = (this && this.__param) || def (paramIndex, decorator) {
  return def (target, key) { decorator(target, key, paramIndex); }
};`

    val awaiterHelper = `
var __awaiter = (this && this.__awaiter) || def (thisArg, _arguments, P, generator) {
  return new (P || (P = Promise))(def (resolve, reject) {
    def fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
    def rejected(value) { try { step(generator.throw(value)); } catch (e) { reject(e); } }
    def step(result) { result.done ? resolve(result.value) : new P(def (resolve) { resolve(result.value); }).then(fulfilled, rejected); }
    step((generator = generator.apply(thisArg, _arguments)).next())
  })
};`

    val compilerOptions = host.getCompilerOptions()
    val languageVersion = getEmitScriptTarget(compilerOptions)
    val modulekind = getEmitModuleKind(compilerOptions)
    val sourceMapDataList: SourceMapData[] = compilerOptions.sourceMap || compilerOptions.inlineSourceMap ? [] : ()
    val emitterDiagnostics = createDiagnosticCollection()
    var emitSkipped = false
    val newLine = host.getNewLine()

    val emitJavaScript = createFileEmitter()
    forEachExpectedEmitFile(host, emitFile, targetSourceFile)

    return {
      emitSkipped,
      diagnostics: emitterDiagnostics.getDiagnostics(),
      sourceMaps: sourceMapDataList
    }

    def isUniqueLocalName(name: String, container: Node): Boolean = {
      for (var node = container; isNodeDescendentOf(node, container); node = node.nextContainer) {
        if (node.locals && hasProperty(node.locals, name)) {
          // We conservatively include alias symbols to cover cases where they're emitted as locals
          if (node.locals[name].flags & (SymbolFlags.Value | SymbolFlags.ExportValue | SymbolFlags.Alias)) {
            return false
          }
        }
      }
      return true
    }

    trait ConvertedLoopState {
      /*
       * set of labels that occurred inside the converted loop
       * used to determine if labeled jump can be emitted as is or it should be dispatched to calling code
       */
      labels?: Map<String>
      /*
       * collection of labeled jumps that transfer control outside the converted loop.
       * maps store association 'label -> labelMarker' where
       * - label - value of label as it appear in code
       * - label marker - return value that should be interpreted by calling code as 'jump to <label>'
       */
      labeledNonLocalBreaks?: Map<String>
      labeledNonLocalContinues?: Map<String>

      /*
       * set of non-labeled jumps that transfer control outside the converted loop
       * used to emit dispatching logic in the caller of converted loop
       */
      nonLocalJumps?: Jump

      /*
       * set of non-labeled jumps that should be interpreted as local
       * i.e. if converted loop contains normal loop or switch statement then inside this loop break should be treated as local jump
       */
      allowedNonLabeledJumps?: Jump

      /*
       * alias for 'arguments' object from the calling code stack frame
       * i.e.
       * for (var x;;) <statement that captures x in closure and uses 'arguments'>
       * should be converted to
       * var loop = def(x) { <code where 'arguments' is replaced with 'arguments_1'> }
       * var arguments_1 = arguments
       * for (var x;;) loop(x)
       * otherwise semantics of the code will be different since 'arguments' inside converted loop body
       * will refer to def that holds converted loop.
       * This value is set on demand.
       */
      argumentsName?: String

      /*
       * alias for 'this' from the calling code stack frame in case if this was used inside the converted loop
       */
      thisName?: String

      /*
       * list of non-block scoped variable declarations that appear inside converted loop
       * such variable declarations should be moved outside the loop body
       * for (var x;;) {
       *   var y = 1
       *   ...
       * }
       * should be converted to
       * var loop = def(x) {
       *  y = 1
       *  ...
       * }
       * var y
       * for (var x;;) loop(x)
       */
      hoistedLocalVariables?: Identifier[]

      /**
       * List of loop out parameters - detailed descripion can be found in the comment to LoopOutParameter
       */
      loopOutParameters?: LoopOutParameter[]
    }

    def setLabeledJump(state: ConvertedLoopState, isBreak: Boolean, labelText: String, labelMarker: String): Unit = {
      if (isBreak) {
        if (!state.labeledNonLocalBreaks) {
          state.labeledNonLocalBreaks = {}
        }
        state.labeledNonLocalBreaks[labelText] = labelMarker
      }
      else {
        if (!state.labeledNonLocalContinues) {
          state.labeledNonLocalContinues = {}
        }
        state.labeledNonLocalContinues[labelText] = labelMarker
      }
    }

    def hoistVariableDeclarationFromLoop(state: ConvertedLoopState, declaration: VariableDeclaration): Unit = {
      if (!state.hoistedLocalVariables) {
        state.hoistedLocalVariables = []
      }

      visit(declaration.name)

      def visit(node: Identifier | BindingPattern) = {
        if (node.kind == SyntaxKind.Identifier) {
          state.hoistedLocalVariables.push((<Identifier>node))
        }
        else {
          for (val element of (<BindingPattern>node).elements) {
            visit(element.name)
          }
        }
      }
    }

    def createFileEmitter(): (jsFilePath: String, sourceMapFilePath: String, sourceFiles: SourceFile[], isBundledEmit: Boolean) => Unit {
      val writer = createTextWriter(newLine)
      val { write, writeTextOfNode, writeLine, increaseIndent, decreaseIndent } = writer

      var sourceMap = compilerOptions.sourceMap || compilerOptions.inlineSourceMap ? createSourceMapWriter(host, writer) : getNullSourceMapWriter()
      var { setSourceFile, emitStart, emitEnd, emitPos } = sourceMap

      var currentSourceFile: SourceFile
      var currentText: String
      var currentLineMap: Int[]
      var currentFileIdentifiers: Map<String>
      var renamedDependencies: Map<String>
      var isEs6Module: Boolean
      var isCurrentFileExternalModule: Boolean

      // name of an exporter def if file is a System external module
      // System.register([...], def (<exporter>) {...})
      // exporting in System modules looks like:
      // var x; ... x = 1
      // =>
      // var x;... exporter("x", x = 1)
      var exportFunctionForFile: String
      var contextObjectForFile: String

      var generatedNameSet: Map<String>
      var nodeToGeneratedName: String[]
      var computedPropertyNamesToGeneratedNames: String[]
      var decoratedClassAliases: String[]

      var convertedLoopState: ConvertedLoopState

      var extendsEmitted: Boolean
      var decorateEmitted: Boolean
      var paramEmitted: Boolean
      var awaiterEmitted: Boolean
      var tempFlags: TempFlags = 0
      var tempVariables: Identifier[]
      var tempParameters: Identifier[]
      var externalImports: (ImportDeclaration | ImportEqualsDeclaration | ExportDeclaration)[]
      var exportSpecifiers: Map<ExportSpecifier[]>
      var exportEquals: ExportAssignment
      var hasExportStarsToExportValues: Boolean

      var detachedCommentsInfo: { nodePos: Int; detachedCommentEndPos: Int }[]

      /** Sourcemap data that will get encoded */
      var sourceMapData: SourceMapData

      /** Is the file being emitted into its own file */
      var isOwnFileEmit: Boolean

      /** If removeComments is true, no leading-comments needed to be emitted **/
      val emitLeadingCommentsOfPosition = compilerOptions.removeComments ? def (pos: Int) { } : emitLeadingCommentsOfPositionWorker

      val setSourceMapWriterEmit = compilerOptions.sourceMap || compilerOptions.inlineSourceMap ? changeSourceMapEmit : def (writer: SourceMapWriter) { }

      val moduleEmitDelegates: Map<(node: SourceFile, emitRelativePathAsModuleName?: Boolean) => Unit> = {
        [ModuleKind.ES6]: emitES6Module,
        [ModuleKind.AMD]: emitAMDModule,
        [ModuleKind.System]: emitSystemModule,
        [ModuleKind.UMD]: emitUMDModule,
        [ModuleKind.CommonJS]: emitCommonJSModule,
      }

      val bundleEmitDelegates: Map<(node: SourceFile, emitRelativePathAsModuleName?: Boolean) => Unit> = {
        [ModuleKind.ES6]() {},
        [ModuleKind.AMD]: emitAMDModule,
        [ModuleKind.System]: emitSystemModule,
        [ModuleKind.UMD]() {},
        [ModuleKind.CommonJS]() {},
      }

      return doEmit

      def doEmit(jsFilePath: String, sourceMapFilePath: String, sourceFiles: SourceFile[], isBundledEmit: Boolean) = {
        sourceMap.initialize(jsFilePath, sourceMapFilePath, sourceFiles, isBundledEmit)
        generatedNameSet = {}
        nodeToGeneratedName = []
        decoratedClassAliases = []
        isOwnFileEmit = !isBundledEmit

        // Emit helpers from all the files
        if (isBundledEmit && modulekind) {
          forEach(sourceFiles, emitEmitHelpers)
        }

        // Do not call emit directly. It does not set the currentSourceFile.
        forEach(sourceFiles, emitSourceFile)

        writeLine()

        val sourceMappingURL = sourceMap.getSourceMappingURL()
        if (sourceMappingURL) {
          write(`//# sourceMappingURL=${sourceMappingURL}`)
        }

        writeEmittedFiles(writer.getText(), jsFilePath, sourceMapFilePath, /*writeByteOrderMark*/ compilerOptions.emitBOM)

        // reset the state
        sourceMap.reset()
        writer.reset()
        currentSourceFile = ()
        currentText = ()
        currentLineMap = ()
        exportFunctionForFile = ()
        contextObjectForFile = ()
        generatedNameSet = ()
        nodeToGeneratedName = ()
        decoratedClassAliases = ()
        computedPropertyNamesToGeneratedNames = ()
        convertedLoopState = ()
        extendsEmitted = false
        decorateEmitted = false
        paramEmitted = false
        awaiterEmitted = false
        tempFlags = 0
        tempVariables = ()
        tempParameters = ()
        externalImports = ()
        exportSpecifiers = ()
        exportEquals = ()
        hasExportStarsToExportValues = ()
        detachedCommentsInfo = ()
        sourceMapData = ()
        isEs6Module = false
        renamedDependencies = ()
        isCurrentFileExternalModule = false
      }

      def emitSourceFile(sourceFile: SourceFile): Unit = {
        currentSourceFile = sourceFile

        currentText = sourceFile.text
        currentLineMap = getLineStarts(sourceFile)
        exportFunctionForFile = ()
        contextObjectForFile = ()
        isEs6Module = sourceFile.symbol && sourceFile.symbol.exports && !!sourceFile.symbol.exports["___esModule"]
        renamedDependencies = sourceFile.renamedDependencies
        currentFileIdentifiers = sourceFile.identifiers
        isCurrentFileExternalModule = isExternalModule(sourceFile)

        setSourceFile(sourceFile)
        emitNodeWithCommentsAndWithoutSourcemap(sourceFile)
      }

      def isUniqueName(name: String): Boolean = {
        return !resolver.hasGlobalName(name) &&
          !hasProperty(currentFileIdentifiers, name) &&
          !hasProperty(generatedNameSet, name)
      }

      // Return the next available name in the pattern _a ... _z, _0, _1, ...
      // TempFlags._i or TempFlags._n may be used to express a preference for that dedicated name.
      // Note that names generated by makeTempVariableName and makeUniqueName will never conflict.
      def makeTempVariableName(flags: TempFlags): String = {
        if (flags && !(tempFlags & flags)) {
          val name = flags == TempFlags._i ? "_i" : "_n"
          if (isUniqueName(name)) {
            tempFlags |= flags
            return name
          }
        }
        while (true) {
          val count = tempFlags & TempFlags.CountMask
          tempFlags++
          // Skip over 'i' and 'n'
          if (count != 8 && count != 13) {
            val name = count < 26 ? "_" + String.fromCharCode(CharacterCodes.a + count) : "_" + (count - 26)
            if (isUniqueName(name)) {
              return name
            }
          }
        }
      }

      // Generate a name that is unique within the current file and doesn't conflict with any names
      // in global scope. The name is formed by adding an '_n' suffix to the specified base name,
      // where n is a positive integer. Note that names generated by makeTempVariableName and
      // makeUniqueName are guaranteed to never conflict.
      def makeUniqueName(baseName: String): String = {
        // Find the first unique 'name_n', where n is a positive Int
        if (baseName.charCodeAt(baseName.length - 1) != CharacterCodes._) {
          baseName += "_"
        }
        var i = 1
        while (true) {
          val generatedName = baseName + i
          if (isUniqueName(generatedName)) {
            return generatedNameSet[generatedName] = generatedName
          }
          i++
        }
      }

      def generateNameForModuleOrEnum(node: ModuleDeclaration | EnumDeclaration) = {
        val name = node.name.text
        // Use module/enum name itself if it is unique, otherwise make a unique variation
        return isUniqueLocalName(name, node) ? name : makeUniqueName(name)
      }

      def generateNameForImportOrExportDeclaration(node: ImportDeclaration | ExportDeclaration) = {
        val expr = getExternalModuleName(node)
        val baseName = expr.kind == SyntaxKind.StringLiteral ?
          escapeIdentifier(makeIdentifierFromModuleName((<LiteralExpression>expr).text)) : "module"
        return makeUniqueName(baseName)
      }

      def generateNameForExportDefault() = {
        return makeUniqueName("default")
      }

      def generateNameForClassExpression() = {
        return makeUniqueName("class")
      }

      def generateNameForNode(node: Node) = {
        switch (node.kind) {
          case SyntaxKind.Identifier:
            return makeUniqueName((<Identifier>node).text)
          case SyntaxKind.ModuleDeclaration:
          case SyntaxKind.EnumDeclaration:
            return generateNameForModuleOrEnum(<ModuleDeclaration | EnumDeclaration>node)
          case SyntaxKind.ImportDeclaration:
          case SyntaxKind.ExportDeclaration:
            return generateNameForImportOrExportDeclaration(<ImportDeclaration | ExportDeclaration>node)
          case SyntaxKind.FunctionDeclaration:
          case SyntaxKind.ClassDeclaration:
          case SyntaxKind.ExportAssignment:
            return generateNameForExportDefault()
          case SyntaxKind.ClassExpression:
            return generateNameForClassExpression()
        }
      }

      def getGeneratedNameForNode(node: Node) = {
        val id = getNodeId(node)
        return nodeToGeneratedName[id] || (nodeToGeneratedName[id] = unescapeIdentifier(generateNameForNode(node)))
      }

      /** Write emitted output to disk */
      def writeEmittedFiles(emitOutput: String, jsFilePath: String, sourceMapFilePath: String, writeByteOrderMark: Boolean) = {
        if (compilerOptions.sourceMap && !compilerOptions.inlineSourceMap) {
          writeFile(host, emitterDiagnostics, sourceMapFilePath, sourceMap.getText(), /*writeByteOrderMark*/ false)
        }

        if (sourceMapDataList) {
          sourceMapDataList.push(sourceMap.getSourceMapData())
        }

        writeFile(host, emitterDiagnostics, jsFilePath, emitOutput, writeByteOrderMark)
      }

      // Create a temporary variable with a unique unused name.
      def createTempVariable(flags: TempFlags): Identifier = {
        val result = <Identifier>createSynthesizedNode(SyntaxKind.Identifier)
        result.text = makeTempVariableName(flags)
        return result
      }

      def recordTempDeclaration(name: Identifier): Unit = {
        if (!tempVariables) {
          tempVariables = []
        }
        tempVariables.push(name)
      }

      def createAndRecordTempVariable(flags: TempFlags): Identifier = {
        val temp = createTempVariable(flags)
        recordTempDeclaration(temp)

        return temp
      }

      def emitTempDeclarations(newLine: Boolean) = {
        if (tempVariables) {
          if (newLine) {
            writeLine()
          }
          else {
            write(" ")
          }
          write("var ")
          emitCommaList(tempVariables)
          write(";")
        }
      }

      /** Emit the text for the given token that comes after startPos
        * This by default writes the text provided with the given tokenKind
        * but if optional emitFn callback is provided the text is emitted using the callback instead of default text
        * @param tokenKind the kind of the token to search and emit
        * @param startPos the position in the source to start searching for the token
        * @param emitFn if given will be invoked to emit the text instead of actual token emit */
      def emitToken(tokenKind: SyntaxKind, startPos: Int, emitFn?: () => Unit) = {
        val tokenStartPos = skipTrivia(currentText, startPos)
        emitPos(tokenStartPos)

        val tokenString = tokenToString(tokenKind)
        if (emitFn) {
          emitFn()
        }
        else {
          write(tokenString)
        }

        val tokenEndPos = tokenStartPos + tokenString.length
        emitPos(tokenEndPos)
        return tokenEndPos
      }

      def emitOptional(prefix: String, node: Node) = {
        if (node) {
          write(prefix)
          emit(node)
        }
      }

      def emitParenthesizedIf(node: Node, parenthesized: Boolean) = {
        if (parenthesized) {
          write("(")
        }
        emit(node)
        if (parenthesized) {
          write(")")
        }
      }

      def emitLinePreservingList(parent: Node, nodes: NodeArray<Node>, allowTrailingComma: Boolean, spacesBetweenBraces: Boolean) = {
        Debug.assert(nodes.length > 0)

        increaseIndent()

        if (nodeStartPositionsAreOnSameLine(parent, nodes[0])) {
          if (spacesBetweenBraces) {
            write(" ")
          }
        }
        else {
          writeLine()
        }

        for (var i = 0, n = nodes.length; i < n; i++) {
          if (i) {
            if (nodeEndIsOnSameLineAsNodeStart(nodes[i - 1], nodes[i])) {
              write(", ")
            }
            else {
              write(",")
              writeLine()
            }
          }

          emit(nodes[i])
        }

        if (nodes.hasTrailingComma && allowTrailingComma) {
          write(",")
        }

        decreaseIndent()

        if (nodeEndPositionsAreOnSameLine(parent, lastOrUndefined(nodes))) {
          if (spacesBetweenBraces) {
            write(" ")
          }
        }
        else {
          writeLine()
        }
      }

      def emitList<TNode extends Node>(nodes: TNode[], start: Int, count: Int, multiLine: Boolean, trailingComma: Boolean, leadingComma?: Boolean, noTrailingNewLine?: Boolean, emitNode?: (node: TNode) => Unit): Int = {
        if (!emitNode) {
          emitNode = emit
        }

        for (var i = 0; i < count; i++) {
          if (multiLine) {
            if (i || leadingComma) {
              write(",")
            }
            writeLine()
          }
          else {
            if (i || leadingComma) {
              write(", ")
            }
          }
          val node = nodes[start + i]
          // This emitting is to make sure we emit following comment properly
          //   ...(x, /*comment1*/ y)...
          //     ^ => node.pos
          // "comment1" is not considered leading comment for "y" but rather
          // considered as trailing comment of the previous node.
          emitTrailingCommentsOfPosition(node.pos)
          emitNode(node)
          leadingComma = true
        }
        if (trailingComma) {
          write(",")
        }
        if (multiLine && !noTrailingNewLine) {
          writeLine()
        }

        return count
      }

      def emitCommaList(nodes: Node[]) = {
        if (nodes) {
          emitList(nodes, 0, nodes.length, /*multiLine*/ false, /*trailingComma*/ false)
        }
      }

      def emitLines(nodes: Node[]) = {
        emitLinesStartingAt(nodes, /*startIndex*/ 0)
      }

      def emitLinesStartingAt(nodes: Node[], startIndex: Int): Unit = {
        for (var i = startIndex; i < nodes.length; i++) {
          writeLine()
          emit(nodes[i])
        }
      }

      def isBinaryOrOctalIntegerLiteral(node: LiteralLikeNode, text: String): Boolean = {
        if (node.kind == SyntaxKind.NumericLiteral && text.length > 1) {
          switch (text.charCodeAt(1)) {
            case CharacterCodes.b:
            case CharacterCodes.B:
            case CharacterCodes.o:
            case CharacterCodes.O:
              return true
          }
        }

        return false
      }

      def emitLiteral(node: LiteralExpression | TemplateLiteralFragment) = {
        val text = getLiteralText(node)

        if ((compilerOptions.sourceMap || compilerOptions.inlineSourceMap) && (node.kind == SyntaxKind.StringLiteral || isTemplateLiteralKind(node.kind))) {
          writer.writeLiteral(text)
        }
        // For versions below ES6, emit binary & octal literals in their canonical decimal form.
        else if (languageVersion < ScriptTarget.ES6 && isBinaryOrOctalIntegerLiteral(node, text)) {
          write(node.text)
        }
        else {
          write(text)
        }
      }

      def getLiteralText(node: LiteralExpression | TemplateLiteralFragment) = {
        // Any template literal or String literal with an extended escape
        // (e.g. "\u{0067}") will need to be downleveled as a escaped String literal.
        if (languageVersion < ScriptTarget.ES6 && (isTemplateLiteralKind(node.kind) || node.hasExtendedUnicodeEscape)) {
          return getQuotedEscapedLiteralText("\"", node.text, "\"")
        }

        // If we don't need to downlevel and we can reach the original source text using
        // the node's parent reference, then simply get the text as it was originally written.
        if (node.parent) {
          return getTextOfNodeFromSourceText(currentText, node)
        }

        // If we can't reach the original source text, use the canonical form if it's a Int,
        // or an escaped quoted form of the original text if it's String-like.
        switch (node.kind) {
          case SyntaxKind.StringLiteral:
            return getQuotedEscapedLiteralText("\"", node.text, "\"")
          case SyntaxKind.NoSubstitutionTemplateLiteral:
            return getQuotedEscapedLiteralText("`", node.text, "`")
          case SyntaxKind.TemplateHead:
            return getQuotedEscapedLiteralText("`", node.text, "${")
          case SyntaxKind.TemplateMiddle:
            return getQuotedEscapedLiteralText("}", node.text, "${")
          case SyntaxKind.TemplateTail:
            return getQuotedEscapedLiteralText("}", node.text, "`")
          case SyntaxKind.NumericLiteral:
            return node.text
        }

        Debug.fail(`Literal kind '${node.kind}' not accounted for.`)
      }

      def getQuotedEscapedLiteralText(leftQuote: String, text: String, rightQuote: String) = {
        return leftQuote + escapeNonAsciiCharacters(escapeString(text)) + rightQuote
      }

      def emitDownlevelRawTemplateLiteral(node: LiteralExpression) = {
        // Find original source text, since we need to emit the raw strings of the tagged template.
        // The raw strings contain the (escaped) strings of what the user wrote.
        // Examples: `\n` is converted to "\\n", a template String with a newline to "\n".
        var text = getTextOfNodeFromSourceText(currentText, node)

        // text contains the original source, it will also contain quotes ("`"), dollar signs and braces ("${" and "}"),
        // thus we need to remove those characters.
        // First template piece starts with "`", others with "}"
        // Last template piece ends with "`", others with "${"
        val isLast = node.kind == SyntaxKind.NoSubstitutionTemplateLiteral || node.kind == SyntaxKind.TemplateTail
        text = text.substring(1, text.length - (isLast ? 1 : 2))

        // Newline normalization:
        // ES6 Spec 11.8.6.1 - Static Semantics of TV's and TRV's
        // <CR><LF> and <CR> LineTerminatorSequences are normalized to <LF> for both TV and TRV.
        text = text.replace(/\r\n?/g, "\n")
        text = escapeString(text)

        write(`"${text}"`)
      }

      def emitDownlevelTaggedTemplateArray(node: TaggedTemplateExpression, literalEmitter: (literal: LiteralExpression | TemplateLiteralFragment) => Unit) = {
        write("[")
        if (node.template.kind == SyntaxKind.NoSubstitutionTemplateLiteral) {
          literalEmitter(<LiteralExpression>node.template)
        }
        else {
          literalEmitter((<TemplateExpression>node.template).head)
          forEach((<TemplateExpression>node.template).templateSpans, (child) => {
            write(", ")
            literalEmitter(child.literal)
          })
        }
        write("]")
      }

      def emitDownlevelTaggedTemplate(node: TaggedTemplateExpression) = {
        val tempVariable = createAndRecordTempVariable(TempFlags.Auto)
        write("(")
        emit(tempVariable)
        write(" = ")
        emitDownlevelTaggedTemplateArray(node, emit)
        write(", ")

        emit(tempVariable)
        write(".raw = ")
        emitDownlevelTaggedTemplateArray(node, emitDownlevelRawTemplateLiteral)
        write(", ")

        emitParenthesizedIf(node.tag, needsParenthesisForPropertyAccessOrInvocation(node.tag))
        write("(")
        emit(tempVariable)

        // Now we emit the expressions
        if (node.template.kind == SyntaxKind.TemplateExpression) {
          forEach((<TemplateExpression>node.template).templateSpans, templateSpan => {
            write(", ")
            val needsParens = templateSpan.expression.kind == SyntaxKind.BinaryExpression
              && (<BinaryExpression>templateSpan.expression).operatorToken.kind == SyntaxKind.CommaToken
            emitParenthesizedIf(templateSpan.expression, needsParens)
          })
        }
        write("))")
      }

      def emitTemplateExpression(node: TemplateExpression): Unit = {
        // In ES6 mode and above, we can simply emit each portion of a template in order, but in
        // ES3 & ES5 we must convert the template expression into a series of String concatenations.
        if (languageVersion >= ScriptTarget.ES6) {
          forEachChild(node, emit)
          return
        }

        val emitOuterParens = isExpression(node.parent)
          && templateNeedsParens(node, <Expression>node.parent)

        if (emitOuterParens) {
          write("(")
        }

        var headEmitted = false
        if (shouldEmitTemplateHead()) {
          emitLiteral(node.head)
          headEmitted = true
        }

        for (var i = 0, n = node.templateSpans.length; i < n; i++) {
          val templateSpan = node.templateSpans[i]

          // Check if the expression has operands and binds its operands less closely than binary '+'.
          // If it does, we need to wrap the expression in parentheses. Otherwise, something like
          //  `abc${ 1 << 2 }`
          // becomes
          //  "abc" + 1 << 2 + ""
          // which is really
          //  ("abc" + 1) << (2 + "")
          // rather than
          //  "abc" + (1 << 2) + ""
          val needsParens = templateSpan.expression.kind != SyntaxKind.ParenthesizedExpression
            && comparePrecedenceToBinaryPlus(templateSpan.expression) != Comparison.GreaterThan

          if (i > 0 || headEmitted) {
            // If this is the first span and the head was not emitted, then this templateSpan's
            // expression will be the first to be emitted. Don't emit the preceding ' + ' in that
            // case.
            write(" + ")
          }

          emitParenthesizedIf(templateSpan.expression, needsParens)

          // Only emit if the literal is non-empty.
          // The binary '+' operator is left-associative, so the first String concatenation
          // with the head will force the result up to this point to be a String.
          // Emitting a '+ ""' has no semantic effect for middles and tails.
          if (templateSpan.literal.text.length != 0) {
            write(" + ")
            emitLiteral(templateSpan.literal)
          }
        }

        if (emitOuterParens) {
          write(")")
        }

        def shouldEmitTemplateHead() = {
          // If this expression has an empty head literal and the first template span has a non-empty
          // literal, then emitting the empty head literal is not necessary.
          //   `${ foo } and ${ bar }`
          // can be emitted as
          //   foo + " and " + bar
          // This is because it is only required that one of the first two operands in the emit
          // output must be a String literal, so that the other operand and all following operands
          // are forced into strings.
          //
          // If the first template span has an empty literal, then the head must still be emitted.
          //   `${ foo }${ bar }`
          // must still be emitted as
          //   "" + foo + bar

          // There is always atleast one templateSpan in this code path, since
          // NoSubstitutionTemplateLiterals are directly emitted via emitLiteral()
          Debug.assert(node.templateSpans.length != 0)

          return node.head.text.length != 0 || node.templateSpans[0].literal.text.length == 0
        }

        def templateNeedsParens(template: TemplateExpression, parent: Expression) = {
          switch (parent.kind) {
            case SyntaxKind.CallExpression:
            case SyntaxKind.NewExpression:
              return (<CallExpression>parent).expression == template
            case SyntaxKind.TaggedTemplateExpression:
            case SyntaxKind.ParenthesizedExpression:
              return false
            default:
              return comparePrecedenceToBinaryPlus(parent) != Comparison.LessThan
          }
        }

        /**
         * Returns whether the expression has lesser, greater,
         * or equal precedence to the binary '+' operator
         */
        def comparePrecedenceToBinaryPlus(expression: Expression): Comparison = {
          // All binary expressions have lower precedence than '+' apart from '*', '/', and '%'
          // which have greater precedence and '-' which has equal precedence.
          // All unary operators have a higher precedence apart from yield.
          // Arrow functions and conditionals have a lower precedence,
          // although we convert the former into regular def expressions in ES5 mode,
          // and in ES6 mode this def won't get called anyway.
          //
          // TODO (drosen): Note that we need to account for the upcoming 'yield' and
          //        spread ('...') unary operators that are anticipated for ES6.
          switch (expression.kind) {
            case SyntaxKind.BinaryExpression:
              switch ((<BinaryExpression>expression).operatorToken.kind) {
                case SyntaxKind.AsteriskToken:
                case SyntaxKind.SlashToken:
                case SyntaxKind.PercentToken:
                  return Comparison.GreaterThan
                case SyntaxKind.PlusToken:
                case SyntaxKind.MinusToken:
                  return Comparison.EqualTo
                default:
                  return Comparison.LessThan
              }
            case SyntaxKind.YieldExpression:
            case SyntaxKind.ConditionalExpression:
              return Comparison.LessThan
            default:
              return Comparison.GreaterThan
          }
        }
      }

      def emitTemplateSpan(span: TemplateSpan) = {
        emit(span.expression)
        emit(span.literal)
      }

      def jsxEmitReact(node: JsxElement | JsxSelfClosingElement) = {
        /// Emit a tag name, which is either '"div"' for lower-cased names, or
        /// 'Div' for upper-cased or dotted names
        def emitTagName(name: Identifier | QualifiedName) = {
          if (name.kind == SyntaxKind.Identifier && isIntrinsicJsxName((<Identifier>name).text)) {
            write("\"")
            emit(name)
            write("\"")
          }
          else {
            emit(name)
          }
        }

        /// Emit an attribute name, which is quoted if it needs to be quoted. Because
        /// these emit into an object literal property name, we don't need to be worried
        /// about keywords, just non-identifier characters
        def emitAttributeName(name: Identifier) = {
          if (/^[A-Za-z_]\w*$/.test(name.text)) {
            emit(name)
          }
          else {
            write("\"")
            emit(name)
            write("\"")
          }
        }

        /// Emit an name/value pair for an attribute (e.g. "x: 3")
        def emitJsxAttribute(node: JsxAttribute) = {
          emitAttributeName(node.name)
          write(": ")
          if (node.initializer) {
            emit(node.initializer)
          }
          else {
            write("true")
          }
        }

        def emitJsxElement(openingNode: JsxOpeningLikeElement, children?: JsxChild[]) = {
          val syntheticReactRef = <Identifier>createSynthesizedNode(SyntaxKind.Identifier)
          syntheticReactRef.text = compilerOptions.reactNamespace ? compilerOptions.reactNamespace : "React"
          syntheticReactRef.parent = openingNode

          // Call React.createElement(tag, ...
          emitLeadingComments(openingNode)
          emitExpressionIdentifier(syntheticReactRef)
          write(".createElement(")
          emitTagName(openingNode.tagName)
          write(", ")

          // Attribute list
          if (openingNode.attributes.length == 0) {
            // When there are no attributes, React wants "null"
            write("null")
          }
          else {
            // Either emit one big object literal (no spread attribs), or
            // a call to React.__spread
            val attrs = openingNode.attributes
            if (forEach(attrs, attr => attr.kind == SyntaxKind.JsxSpreadAttribute)) {
              emitExpressionIdentifier(syntheticReactRef)
              write(".__spread(")

              var haveOpenedObjectLiteral = false
              for (var i = 0; i < attrs.length; i++) {
                if (attrs[i].kind == SyntaxKind.JsxSpreadAttribute) {
                  // If this is the first argument, we need to emit a {} as the first argument
                  if (i == 0) {
                    write("{}, ")
                  }

                  if (haveOpenedObjectLiteral) {
                    write("}")
                    haveOpenedObjectLiteral = false
                  }
                  if (i > 0) {
                    write(", ")
                  }
                  emit((<JsxSpreadAttribute>attrs[i]).expression)
                }
                else {
                  Debug.assert(attrs[i].kind == SyntaxKind.JsxAttribute)
                  if (haveOpenedObjectLiteral) {
                    write(", ")
                  }
                  else {
                    haveOpenedObjectLiteral = true
                    if (i > 0) {
                      write(", ")
                    }
                    write("{")
                  }
                  emitJsxAttribute(<JsxAttribute>attrs[i])
                }
              }
              if (haveOpenedObjectLiteral) write("}")

              write(")"); // closing paren to React.__spread(
            }
            else {
              // One object literal with all the attributes in them
              write("{")
              for (var i = 0, n = attrs.length; i < n; i++) {
                if (i > 0) {
                  write(", ")
                }
                emitJsxAttribute(<JsxAttribute>attrs[i])
              }
              write("}")
            }
          }

          // Children
          if (children) {
            var firstChild: JsxChild
            var multipleEmittableChildren = false

            for (var i = 0, n = children.length; i < n; i++) {
              val jsxChild = children[i]

              if (isJsxChildEmittable(jsxChild)) {
                // we need to decide whether to emit in single line or multiple lines as indented list
                // store firstChild reference, if we see another emittable child, then emit accordingly
                if (!firstChild) {
                  write(", ")
                  firstChild = jsxChild
                }
                else {
                  // more than one emittable child, emit indented list
                  if (!multipleEmittableChildren) {
                    multipleEmittableChildren = true
                    increaseIndent()
                    writeLine()
                    emit(firstChild)
                  }

                  write(", ")
                  writeLine()
                  emit(jsxChild)
                }
              }
            }

            if (multipleEmittableChildren) {
              decreaseIndent()
            }
            else if (firstChild) {
              if (firstChild.kind != SyntaxKind.JsxElement && firstChild.kind != SyntaxKind.JsxSelfClosingElement) {
                emit(firstChild)
              }
              else {
                // If the only child is jsx element, put it on a new indented line
                increaseIndent()
                writeLine()
                emit(firstChild)
                writeLine()
                decreaseIndent()
              }
            }
          }

          // Closing paren
          write(")"); // closes "React.createElement("
          emitTrailingComments(openingNode)
        }

        if (node.kind == SyntaxKind.JsxElement) {
          emitJsxElement((<JsxElement>node).openingElement, (<JsxElement>node).children)
        }
        else {
          Debug.assert(node.kind == SyntaxKind.JsxSelfClosingElement)
          emitJsxElement(<JsxSelfClosingElement>node)
        }
      }

      def jsxEmitPreserve(node: JsxElement | JsxSelfClosingElement) = {
        def emitJsxAttribute(node: JsxAttribute) = {
          emit(node.name)
          if (node.initializer) {
            write("=")
            emit(node.initializer)
          }
        }

        def emitJsxSpreadAttribute(node: JsxSpreadAttribute) = {
          write("{...")
          emit(node.expression)
          write("}")
        }

        def emitAttributes(attribs: NodeArray<JsxAttribute | JsxSpreadAttribute>) = {
          for (var i = 0, n = attribs.length; i < n; i++) {
            if (i > 0) {
              write(" ")
            }

            if (attribs[i].kind == SyntaxKind.JsxSpreadAttribute) {
              emitJsxSpreadAttribute(<JsxSpreadAttribute>attribs[i])
            }
            else {
              Debug.assert(attribs[i].kind == SyntaxKind.JsxAttribute)
              emitJsxAttribute(<JsxAttribute>attribs[i])
            }
          }
        }

        def emitJsxOpeningOrSelfClosingElement(node: JsxOpeningElement | JsxSelfClosingElement) = {
          write("<")
          emit(node.tagName)
          if (node.attributes.length > 0 || (node.kind == SyntaxKind.JsxSelfClosingElement)) {
            write(" ")
          }

          emitAttributes(node.attributes)

          if (node.kind == SyntaxKind.JsxSelfClosingElement) {
            write("/>")
          }
          else {
            write(">")
          }
        }

        def emitJsxClosingElement(node: JsxClosingElement) = {
          write("</")
          emit(node.tagName)
          write(">")
        }

        def emitJsxElement(node: JsxElement) = {
          emitJsxOpeningOrSelfClosingElement(node.openingElement)

          for (var i = 0, n = node.children.length; i < n; i++) {
            emit(node.children[i])
          }

          emitJsxClosingElement(node.closingElement)
        }

        if (node.kind == SyntaxKind.JsxElement) {
          emitJsxElement(<JsxElement>node)
        }
        else {
          Debug.assert(node.kind == SyntaxKind.JsxSelfClosingElement)
          emitJsxOpeningOrSelfClosingElement(<JsxSelfClosingElement>node)
        }
      }

      // This def specifically handles numeric/String literals for enum and accessor 'identifiers'.
      // In a sense, it does not actually emit identifiers as much as it declares a name for a specific property.
      // For example, this is utilized when feeding in a result to Object.defineProperty.
      def emitExpressionForPropertyName(node: DeclarationName) = {
        Debug.assert(node.kind != SyntaxKind.BindingElement)

        if (node.kind == SyntaxKind.StringLiteral) {
          emitLiteral(<LiteralExpression>node)
        }
        else if (node.kind == SyntaxKind.ComputedPropertyName) {
          // if this is a decorated computed property, we will need to capture the result
          // of the property expression so that we can apply decorators later. This is to ensure
          // we don't introduce unintended side effects:
          //
          //   class C {
          //   [_a = x]() { }
          //   }
          //
          // The emit for the decorated computed property decorator is:
          //
          //   __decorate([dec], C.prototype, _a, Object.getOwnPropertyDescriptor(C.prototype, _a))
          //
          if (nodeIsDecorated(node.parent)) {
            if (!computedPropertyNamesToGeneratedNames) {
              computedPropertyNamesToGeneratedNames = []
            }

            var generatedName = computedPropertyNamesToGeneratedNames[getNodeId(node)]
            if (generatedName) {
              // we have already generated a variable for this node, write that value instead.
              write(generatedName)
              return
            }

            generatedName = createAndRecordTempVariable(TempFlags.Auto).text
            computedPropertyNamesToGeneratedNames[getNodeId(node)] = generatedName
            write(generatedName)
            write(" = ")
          }

          emit((<ComputedPropertyName>node).expression)
        }
        else {
          write("\"")

          if (node.kind == SyntaxKind.NumericLiteral) {
            write((<LiteralExpression>node).text)
          }
          else {
            writeTextOfNode(currentText, node)
          }

          write("\"")
        }
      }

      def isExpressionIdentifier(node: Node): Boolean = {
        val parent = node.parent
        switch (parent.kind) {
          case SyntaxKind.ArrayLiteralExpression:
          case SyntaxKind.AsExpression:
          case SyntaxKind.BinaryExpression:
          case SyntaxKind.CallExpression:
          case SyntaxKind.CaseClause:
          case SyntaxKind.ComputedPropertyName:
          case SyntaxKind.ConditionalExpression:
          case SyntaxKind.Decorator:
          case SyntaxKind.DeleteExpression:
          case SyntaxKind.DoStatement:
          case SyntaxKind.ElementAccessExpression:
          case SyntaxKind.ExportAssignment:
          case SyntaxKind.ExpressionStatement:
          case SyntaxKind.ExpressionWithTypeArguments:
          case SyntaxKind.ForStatement:
          case SyntaxKind.ForInStatement:
          case SyntaxKind.ForOfStatement:
          case SyntaxKind.IfStatement:
          case SyntaxKind.JsxClosingElement:
          case SyntaxKind.JsxSelfClosingElement:
          case SyntaxKind.JsxOpeningElement:
          case SyntaxKind.JsxSpreadAttribute:
          case SyntaxKind.JsxExpression:
          case SyntaxKind.NewExpression:
          case SyntaxKind.ParenthesizedExpression:
          case SyntaxKind.PostfixUnaryExpression:
          case SyntaxKind.PrefixUnaryExpression:
          case SyntaxKind.ReturnStatement:
          case SyntaxKind.ShorthandPropertyAssignment:
          case SyntaxKind.SpreadElementExpression:
          case SyntaxKind.SwitchStatement:
          case SyntaxKind.TaggedTemplateExpression:
          case SyntaxKind.TemplateSpan:
          case SyntaxKind.ThrowStatement:
          case SyntaxKind.TypeAssertionExpression:
          case SyntaxKind.TypeOfExpression:
          case SyntaxKind.VoidExpression:
          case SyntaxKind.WhileStatement:
          case SyntaxKind.WithStatement:
          case SyntaxKind.YieldExpression:
            return true
          case SyntaxKind.BindingElement:
          case SyntaxKind.EnumMember:
          case SyntaxKind.Parameter:
          case SyntaxKind.PropertyAssignment:
          case SyntaxKind.PropertyDeclaration:
          case SyntaxKind.VariableDeclaration:
            return (<BindingElement | EnumMember | ParameterDeclaration | PropertyAssignment | PropertyDeclaration | VariableDeclaration>parent).initializer == node
          case SyntaxKind.PropertyAccessExpression:
            return (<ExpressionStatement>parent).expression == node
          case SyntaxKind.ArrowFunction:
          case SyntaxKind.FunctionExpression:
            return (<FunctionLikeDeclaration>parent).body == node
          case SyntaxKind.ImportEqualsDeclaration:
            return (<ImportEqualsDeclaration>parent).moduleReference == node
          case SyntaxKind.QualifiedName:
            return (<QualifiedName>parent).left == node
        }
        return false
      }

      def emitExpressionIdentifier(node: Identifier) = {
        val container = resolver.getReferencedExportContainer(node)
        if (container) {
          if (container.kind == SyntaxKind.SourceFile) {
            // Identifier references module export
            if (modulekind != ModuleKind.ES6 && modulekind != ModuleKind.System) {
              write("exports.")
            }
          }
          else {
            // Identifier references package export
            write(getGeneratedNameForNode(container))
            write(".")
          }
        }
        else {
          if (modulekind != ModuleKind.ES6) {
            val declaration = resolver.getReferencedImportDeclaration(node)
            if (declaration) {
              if (declaration.kind == SyntaxKind.ImportClause) {
                // Identifier references default import
                write(getGeneratedNameForNode(<ImportDeclaration>declaration.parent))
                write(languageVersion == ScriptTarget.ES3 ? "[\"default\"]" : ".default")
                return
              }
              else if (declaration.kind == SyntaxKind.ImportSpecifier) {
                // Identifier references named import
                write(getGeneratedNameForNode(<ImportDeclaration>declaration.parent.parent.parent))
                val name =  (<ImportSpecifier>declaration).propertyName || (<ImportSpecifier>declaration).name
                val identifier = getTextOfNodeFromSourceText(currentText, name)
                if (languageVersion == ScriptTarget.ES3 && identifier == "default") {
                  write(`["default"]`)
                }
                else {
                  write(".")
                  write(identifier)
                }
                return
              }
            }
          }

          if (languageVersion < ScriptTarget.ES6) {
            val declaration = resolver.getReferencedDeclarationWithCollidingName(node)
            if (declaration) {
              write(getGeneratedNameForNode(declaration.name))
              return
            }
          }
          else if (resolver.getNodeCheckFlags(node) & NodeCheckFlags.BodyScopedClassBinding) {
            // Due to the emit for class decorators, any reference to the class from inside of the class body
            // must instead be rewritten to point to a temporary variable to avoid issues with the double-bind
            // behavior of class names in ES6.
            val declaration = resolver.getReferencedValueDeclaration(node)
            if (declaration) {
              val classAlias = decoratedClassAliases[getNodeId(declaration)]
              if (classAlias != ()) {
                write(classAlias)
                return
              }
            }
          }
        }

        if (nodeIsSynthesized(node)) {
          write(node.text)
        }
        else {
          writeTextOfNode(currentText, node)
        }
      }

      def isNameOfNestedBlockScopedRedeclarationOrCapturedBinding(node: Identifier) = {
        if (languageVersion < ScriptTarget.ES6) {
          val parent = node.parent
          switch (parent.kind) {
            case SyntaxKind.BindingElement:
            case SyntaxKind.ClassDeclaration:
            case SyntaxKind.EnumDeclaration:
            case SyntaxKind.VariableDeclaration:
              return (<Declaration>parent).name == node && resolver.isDeclarationWithCollidingName(<Declaration>parent)
          }
        }
        return false
      }

      def emitIdentifier(node: Identifier) = {
        if (convertedLoopState) {
          if (node.text == "arguments" && resolver.isArgumentsLocalBinding(node)) {
            // in converted loop body arguments cannot be used directly.
            val name = convertedLoopState.argumentsName || (convertedLoopState.argumentsName = makeUniqueName("arguments"))
            write(name)
            return
          }
        }

        if (!node.parent) {
          write(node.text)
        }
        else if (isExpressionIdentifier(node)) {
          emitExpressionIdentifier(node)
        }
        else if (isNameOfNestedBlockScopedRedeclarationOrCapturedBinding(node)) {
          write(getGeneratedNameForNode(node))
        }
        else if (nodeIsSynthesized(node)) {
          write(node.text)
        }
        else {
          writeTextOfNode(currentText, node)
        }
      }

      def emitThis(node: Node) = {
        if (resolver.getNodeCheckFlags(node) & NodeCheckFlags.LexicalThis) {
          write("_this")
        }
        else if (convertedLoopState) {
          write(convertedLoopState.thisName || (convertedLoopState.thisName = makeUniqueName("this")))
        }
        else {
          write("this")
        }
      }

      def emitSuper(node: Node) = {
        if (languageVersion >= ScriptTarget.ES6) {
          write("super")
        }
        else {
          val flags = resolver.getNodeCheckFlags(node)
          if (flags & NodeCheckFlags.SuperInstance) {
            write("_super.prototype")
          }
          else {
            write("_super")
          }
        }
      }

      def emitObjectBindingPattern(node: BindingPattern) = {
        write("{ ")
        val elements = node.elements
        emitList(elements, 0, elements.length, /*multiLine*/ false, /*trailingComma*/ elements.hasTrailingComma)
        write(" }")
      }

      def emitArrayBindingPattern(node: BindingPattern) = {
        write("[")
        val elements = node.elements
        emitList(elements, 0, elements.length, /*multiLine*/ false, /*trailingComma*/ elements.hasTrailingComma)
        write("]")
      }

      def emitBindingElement(node: BindingElement) = {
        if (node.propertyName) {
          emit(node.propertyName)
          write(": ")
        }
        if (node.dotDotDotToken) {
          write("...")
        }
        if (isBindingPattern(node.name)) {
          emit(node.name)
        }
        else {
          emitModuleMemberName(node)
        }
        emitOptional(" = ", node.initializer)
      }

      def emitSpreadElementExpression(node: SpreadElementExpression) = {
        write("...")
        emit((<SpreadElementExpression>node).expression)
      }

      def emitYieldExpression(node: YieldExpression) = {
        write(tokenToString(SyntaxKind.YieldKeyword))
        if (node.asteriskToken) {
          write("*")
        }
        if (node.expression) {
          write(" ")
          emit(node.expression)
        }
      }

      def emitAwaitExpression(node: AwaitExpression) = {
        val needsParenthesis = needsParenthesisForAwaitExpressionAsYield(node)
        if (needsParenthesis) {
          write("(")
        }
        write(tokenToString(SyntaxKind.YieldKeyword))
        write(" ")
        emit(node.expression)
        if (needsParenthesis) {
          write(")")
        }
      }

      def needsParenthesisForAwaitExpressionAsYield(node: AwaitExpression) = {
        if (node.parent.kind == SyntaxKind.BinaryExpression && !isAssignmentOperator((<BinaryExpression>node.parent).operatorToken.kind)) {
          return true
        }
        else if (node.parent.kind == SyntaxKind.ConditionalExpression && (<ConditionalExpression>node.parent).condition == node) {
          return true
        }

        return false
      }

      def needsParenthesisForPropertyAccessOrInvocation(node: Expression) = {
        switch (node.kind) {
          case SyntaxKind.Identifier:
          case SyntaxKind.ArrayLiteralExpression:
          case SyntaxKind.PropertyAccessExpression:
          case SyntaxKind.ElementAccessExpression:
          case SyntaxKind.CallExpression:
          case SyntaxKind.ParenthesizedExpression:
            // This list is not exhaustive and only includes those cases that are relevant
            // to the check in emitArrayLiteral. More cases can be added as needed.
            return false
        }
        return true
      }

      def emitListWithSpread(elements: Expression[], needsUniqueCopy: Boolean, multiLine: Boolean, trailingComma: Boolean, useConcat: Boolean) = {
        var pos = 0
        var group = 0
        val length = elements.length
        while (pos < length) {
          // Emit using the pattern <group0>.concat(<group1>, <group2>, ...)
          if (group == 1 && useConcat) {
            write(".concat(")
          }
          else if (group > 0) {
            write(", ")
          }
          var e = elements[pos]
          if (e.kind == SyntaxKind.SpreadElementExpression) {
            e = (<SpreadElementExpression>e).expression
            emitParenthesizedIf(e, /*parenthesized*/ group == 0 && needsParenthesisForPropertyAccessOrInvocation(e))
            pos++
            if (pos == length && group == 0 && needsUniqueCopy && e.kind != SyntaxKind.ArrayLiteralExpression) {
              write(".slice()")
            }
          }
          else {
            var i = pos
            while (i < length && elements[i].kind != SyntaxKind.SpreadElementExpression) {
              i++
            }
            write("[")
            if (multiLine) {
              increaseIndent()
            }
            emitList(elements, pos, i - pos, multiLine, trailingComma && i == length)
            if (multiLine) {
              decreaseIndent()
            }
            write("]")
            pos = i
          }
          group++
        }
        if (group > 1) {
          if (useConcat) {
            write(")")
          }
        }
      }

      def isSpreadElementExpression(node: Node) = {
        return node.kind == SyntaxKind.SpreadElementExpression
      }

      def emitArrayLiteral(node: ArrayLiteralExpression) = {
        val elements = node.elements
        if (elements.length == 0) {
          write("[]")
        }
        else if (languageVersion >= ScriptTarget.ES6 || !forEach(elements, isSpreadElementExpression)) {
          write("[")
          emitLinePreservingList(node, node.elements, elements.hasTrailingComma, /*spacesBetweenBraces*/ false)
          write("]")
        }
        else {
          emitListWithSpread(elements, /*needsUniqueCopy*/ true, /*multiLine*/ node.multiLine,
            /*trailingComma*/ elements.hasTrailingComma, /*useConcat*/ true)
        }
      }

      def emitObjectLiteralBody(node: ObjectLiteralExpression, numElements: Int): Unit = {
        if (numElements == 0) {
          write("{}")
          return
        }

        write("{")

        if (numElements > 0) {
          val properties = node.properties

          // If we are not doing a downlevel transformation for object literals,
          // then try to preserve the original shape of the object literal.
          // Otherwise just try to preserve the formatting.
          if (numElements == properties.length) {
            emitLinePreservingList(node, properties, /*allowTrailingComma*/ languageVersion >= ScriptTarget.ES5, /*spacesBetweenBraces*/ true)
          }
          else {
            val multiLine = node.multiLine
            if (!multiLine) {
              write(" ")
            }
            else {
              increaseIndent()
            }

            emitList(properties, 0, numElements, /*multiLine*/ multiLine, /*trailingComma*/ false)

            if (!multiLine) {
              write(" ")
            }
            else {
              decreaseIndent()
            }
          }
        }

        write("}")
      }

      def emitDownlevelObjectLiteralWithComputedProperties(node: ObjectLiteralExpression, firstComputedPropertyIndex: Int) = {
        val multiLine = node.multiLine
        val properties = node.properties

        write("(")

        if (multiLine) {
          increaseIndent()
        }

        // For computed properties, we need to create a unique handle to the object
        // literal so we can modify it without risking internal assignments tainting the object.
        val tempVar = createAndRecordTempVariable(TempFlags.Auto)

        // Write out the first non-computed properties
        // (or all properties if none of them are computed),
        // then emit the rest through indexing on the temp variable.
        emit(tempVar)
        write(" = ")
        emitObjectLiteralBody(node, firstComputedPropertyIndex)

        for (var i = firstComputedPropertyIndex, n = properties.length; i < n; i++) {
          writeComma()

          val property = properties[i]

          emitStart(property)
          if (property.kind == SyntaxKind.GetAccessor || property.kind == SyntaxKind.SetAccessor) {
            // TODO (drosen): Reconcile with 'emitMemberFunctions'.
            val accessors = getAllAccessorDeclarations(node.properties, <AccessorDeclaration>property)
            if (property != accessors.firstAccessor) {
              continue
            }
            write("Object.defineProperty(")
            emit(tempVar)
            write(", ")
            emitStart(node.name)
            emitExpressionForPropertyName(property.name)
            emitEnd(property.name)
            write(", {")
            increaseIndent()
            if (accessors.getAccessor) {
              writeLine()
              emitLeadingComments(accessors.getAccessor)
              write("get: ")
              emitStart(accessors.getAccessor)
              write("def ")
              emitSignatureAndBody(accessors.getAccessor)
              emitEnd(accessors.getAccessor)
              emitTrailingComments(accessors.getAccessor)
              write(",")
            }
            if (accessors.setAccessor) {
              writeLine()
              emitLeadingComments(accessors.setAccessor)
              write("set: ")
              emitStart(accessors.setAccessor)
              write("def ")
              emitSignatureAndBody(accessors.setAccessor)
              emitEnd(accessors.setAccessor)
              emitTrailingComments(accessors.setAccessor)
              write(",")
            }
            writeLine()
            write("enumerable: true,")
            writeLine()
            write("configurable: true")
            decreaseIndent()
            writeLine()
            write("})")
            emitEnd(property)
          }
          else {
            emitLeadingComments(property)
            emitStart(property.name)
            emit(tempVar)
            emitMemberAccessForPropertyName(property.name)
            emitEnd(property.name)

            write(" = ")

            if (property.kind == SyntaxKind.PropertyAssignment) {
              emit((<PropertyAssignment>property).initializer)
            }
            else if (property.kind == SyntaxKind.ShorthandPropertyAssignment) {
              emitExpressionIdentifier((<ShorthandPropertyAssignment>property).name)
            }
            else if (property.kind == SyntaxKind.MethodDeclaration) {
              emitFunctionDeclaration(<MethodDeclaration>property)
            }
            else {
              Debug.fail("ObjectLiteralElement type not accounted for: " + property.kind)
            }
          }

          emitEnd(property)
        }

        writeComma()
        emit(tempVar)

        if (multiLine) {
          decreaseIndent()
          writeLine()
        }

        write(")")

        def writeComma() = {
          if (multiLine) {
            write(",")
            writeLine()
          }
          else {
            write(", ")
          }
        }
      }

      def emitObjectLiteral(node: ObjectLiteralExpression): Unit = {
        val properties = node.properties

        if (languageVersion < ScriptTarget.ES6) {
          val numProperties = properties.length

          // Find the first computed property.
          // Everything until that point can be emitted as part of the initial object literal.
          var numInitialNonComputedProperties = numProperties
          for (var i = 0, n = properties.length; i < n; i++) {
            if (properties[i].name.kind == SyntaxKind.ComputedPropertyName) {
              numInitialNonComputedProperties = i
              break
            }
          }

          val hasComputedProperty = numInitialNonComputedProperties != properties.length
          if (hasComputedProperty) {
            emitDownlevelObjectLiteralWithComputedProperties(node, numInitialNonComputedProperties)
            return
          }
        }

        // Ordinary case: either the object has no computed properties
        // or we're compiling with an ES6+ target.
        emitObjectLiteralBody(node, properties.length)
      }

      def createBinaryExpression(left: Expression, operator: SyntaxKind, right: Expression, startsOnNewLine?: Boolean): BinaryExpression = {
        val result = <BinaryExpression>createSynthesizedNode(SyntaxKind.BinaryExpression, startsOnNewLine)
        result.operatorToken = createSynthesizedNode(operator)
        result.left = left
        result.right = right

        return result
      }

      def createPropertyAccessExpression(expression: Expression, name: Identifier): PropertyAccessExpression = {
        val result = <PropertyAccessExpression>createSynthesizedNode(SyntaxKind.PropertyAccessExpression)
        result.expression = parenthesizeForAccess(expression)
        result.dotToken = createSynthesizedNode(SyntaxKind.DotToken)
        result.name = name
        return result
      }

      def createElementAccessExpression(expression: Expression, argumentExpression: Expression): ElementAccessExpression = {
        val result = <ElementAccessExpression>createSynthesizedNode(SyntaxKind.ElementAccessExpression)
        result.expression = parenthesizeForAccess(expression)
        result.argumentExpression = argumentExpression

        return result
      }

      def parenthesizeForAccess(expr: Expression): LeftHandSideExpression = {
        // When diagnosing whether the expression needs parentheses, the decision should be based
        // on the innermost expression in a chain of nested type assertions.
        while (expr.kind == SyntaxKind.TypeAssertionExpression || expr.kind == SyntaxKind.AsExpression) {
          expr = (<AssertionExpression>expr).expression
        }

        // isLeftHandSideExpression is almost the correct criterion for when it is not necessary
        // to parenthesize the expression before a dot. The known exceptions are:
        //
        //  NewExpression:
        //     new C.x    -> not the same as (new C).x
        //  NumberLiteral
        //     1.x      -> not the same as (1).x
        //
        if (isLeftHandSideExpression(expr) &&
          expr.kind != SyntaxKind.NewExpression &&
          expr.kind != SyntaxKind.NumericLiteral) {

          return <LeftHandSideExpression>expr
        }
        val node = <ParenthesizedExpression>createSynthesizedNode(SyntaxKind.ParenthesizedExpression)
        node.expression = expr
        return node
      }

      def emitComputedPropertyName(node: ComputedPropertyName) = {
        write("[")
        emitExpressionForPropertyName(node)
        write("]")
      }

      def emitMethod(node: MethodDeclaration) = {
        if (languageVersion >= ScriptTarget.ES6 && node.asteriskToken) {
          write("*")
        }

        emit(node.name)
        if (languageVersion < ScriptTarget.ES6) {
          write(": def ")
        }
        emitSignatureAndBody(node)
      }

      def emitPropertyAssignment(node: PropertyDeclaration) = {
        emit(node.name)
        write(": ")
        // This is to ensure that we emit comment in the following case:
        //    For example:
        //      obj = {
        //        id: /*comment1*/ ()=>Unit
        //      }
        // "comment1" is not considered to be leading comment for node.initializer
        // but rather a trailing comment on the previous node.
        emitTrailingCommentsOfPosition(node.initializer.pos)
        emit(node.initializer)
      }

      // Return true if identifier resolves to an exported member of a package
      def isNamespaceExportReference(node: Identifier) = {
        val container = resolver.getReferencedExportContainer(node)
        return container && container.kind != SyntaxKind.SourceFile
      }

      def emitShorthandPropertyAssignment(node: ShorthandPropertyAssignment) = {
        // The name property of a short-hand property assignment is considered an expression position, so here
        // we manually emit the identifier to avoid rewriting.
        writeTextOfNode(currentText, node.name)
        // If emitting pre-ES6 code, or if the name requires rewriting when resolved as an expression identifier,
        // we emit a normal property assignment. For example:
        //   module m {
        //     var y
        //   }
        //   module m {
        //     var obj = { y }
        //   }
        // Here we need to emit obj = { y : m.y } regardless of the output target.
        if (modulekind != ModuleKind.ES6 || isNamespaceExportReference(node.name)) {
          // Emit identifier as an identifier
          write(": ")
          emit(node.name)
        }

        if (languageVersion >= ScriptTarget.ES6 && node.objectAssignmentInitializer) {
          write(" = ")
          emit(node.objectAssignmentInitializer)
        }
      }

      def tryEmitConstantValue(node: PropertyAccessExpression | ElementAccessExpression): Boolean = {
        val constantValue = tryGetConstEnumValue(node)
        if (constantValue != ()) {
          write(constantValue.toString())
          if (!compilerOptions.removeComments) {
            val propertyName: String = node.kind == SyntaxKind.PropertyAccessExpression ? declarationNameToString((<PropertyAccessExpression>node).name) : getTextOfNode((<ElementAccessExpression>node).argumentExpression)
            write(" /* " + propertyName + " */")
          }
          return true
        }
        return false
      }

      def tryGetConstEnumValue(node: Node): Int = {
        if (compilerOptions.isolatedModules) {
          return ()
        }

        return node.kind == SyntaxKind.PropertyAccessExpression || node.kind == SyntaxKind.ElementAccessExpression
          ? resolver.getConstantValue(<PropertyAccessExpression | ElementAccessExpression>node)
          : ()
      }

      // Returns 'true' if the code was actually indented, false otherwise.
      // If the code is not indented, an optional valueToWriteWhenNotIndenting will be
      // emitted instead.
      def indentIfOnDifferentLines(parent: Node, node1: Node, node2: Node, valueToWriteWhenNotIndenting?: String): Boolean = {
        val realNodesAreOnDifferentLines = !nodeIsSynthesized(parent) && !nodeEndIsOnSameLineAsNodeStart(node1, node2)

        // Always use a newline for synthesized code if the synthesizer desires it.
        val synthesizedNodeIsOnDifferentLine = synthesizedNodeStartsOnNewLine(node2)

        if (realNodesAreOnDifferentLines || synthesizedNodeIsOnDifferentLine) {
          increaseIndent()
          writeLine()
          return true
        }
        else {
          if (valueToWriteWhenNotIndenting) {
            write(valueToWriteWhenNotIndenting)
          }
          return false
        }
      }

      def emitPropertyAccess(node: PropertyAccessExpression) = {
        if (tryEmitConstantValue(node)) {
          return
        }

        if (languageVersion == ScriptTarget.ES6 &&
          node.expression.kind == SyntaxKind.SuperKeyword &&
          isInAsyncMethodWithSuperInES6(node)) {
          val name = <StringLiteral>createSynthesizedNode(SyntaxKind.StringLiteral)
          name.text = node.name.text
          emitSuperAccessInAsyncMethod(node.expression, name)
          return
        }

        emit(node.expression)
        val indentedBeforeDot = indentIfOnDifferentLines(node, node.expression, node.dotToken)

        // 1 .toString is a valid property access, emit a space after the literal
        // Also emit a space if expression is a integer val enum value - it will appear in generated code as numeric literal
        var shouldEmitSpace = false
        if (!indentedBeforeDot) {
          if (node.expression.kind == SyntaxKind.NumericLiteral) {
            // check if numeric literal was originally written with a dot
            val text = getTextOfNodeFromSourceText(currentText, node.expression)
            shouldEmitSpace = text.indexOf(tokenToString(SyntaxKind.DotToken)) < 0
          }
          else {
            // check if constant enum value is integer
            val constantValue = tryGetConstEnumValue(node.expression)
            // isFinite handles cases when constantValue is ()
            shouldEmitSpace = isFinite(constantValue) && Math.floor(constantValue) == constantValue
          }
        }

        if (shouldEmitSpace) {
          write(" .")
        }
        else {
          write(".")
        }

        val indentedAfterDot = indentIfOnDifferentLines(node, node.dotToken, node.name)
        emit(node.name)
        decreaseIndentIf(indentedBeforeDot, indentedAfterDot)
      }

      def emitQualifiedName(node: QualifiedName) = {
        emit(node.left)
        write(".")
        emit(node.right)
      }

      def emitQualifiedNameAsExpression(node: QualifiedName, useFallback: Boolean) = {
        if (node.left.kind == SyntaxKind.Identifier) {
          emitEntityNameAsExpression(node.left, useFallback)
        }
        else if (useFallback) {
          val temp = createAndRecordTempVariable(TempFlags.Auto)
          write("(")
          emitNodeWithoutSourceMap(temp)
          write(" = ")
          emitEntityNameAsExpression(node.left, /*useFallback*/ true)
          write(") && ")
          emitNodeWithoutSourceMap(temp)
        }
        else {
          emitEntityNameAsExpression(node.left, /*useFallback*/ false)
        }

        write(".")
        emit(node.right)
      }

      def emitEntityNameAsExpression(node: EntityName | Expression, useFallback: Boolean) = {
        switch (node.kind) {
          case SyntaxKind.Identifier:
            if (useFallback) {
              write("typeof ")
              emitExpressionIdentifier(<Identifier>node)
              write(" != '()' && ")
            }

            emitExpressionIdentifier(<Identifier>node)
            break

          case SyntaxKind.QualifiedName:
            emitQualifiedNameAsExpression(<QualifiedName>node, useFallback)
            break

          default:
            emitNodeWithoutSourceMap(node)
            break
        }
      }

      def emitIndexedAccess(node: ElementAccessExpression) = {
        if (tryEmitConstantValue(node)) {
          return
        }

        if (languageVersion == ScriptTarget.ES6 &&
          node.expression.kind == SyntaxKind.SuperKeyword &&
          isInAsyncMethodWithSuperInES6(node)) {
          emitSuperAccessInAsyncMethod(node.expression, node.argumentExpression)
          return
        }

        emit(node.expression)
        write("[")
        emit(node.argumentExpression)
        write("]")
      }

      def hasSpreadElement(elements: Expression[]) = {
        return forEach(elements, e => e.kind == SyntaxKind.SpreadElementExpression)
      }

      def skipParentheses(node: Expression): Expression = {
        while (node.kind == SyntaxKind.ParenthesizedExpression || node.kind == SyntaxKind.TypeAssertionExpression || node.kind == SyntaxKind.AsExpression) {
          node = (<ParenthesizedExpression | AssertionExpression>node).expression
        }
        return node
      }

      def emitCallTarget(node: Expression): Expression = {
        if (node.kind == SyntaxKind.Identifier || node.kind == SyntaxKind.ThisKeyword || node.kind == SyntaxKind.SuperKeyword) {
          emit(node)
          return node
        }
        val temp = createAndRecordTempVariable(TempFlags.Auto)

        write("(")
        emit(temp)
        write(" = ")
        emit(node)
        write(")")
        return temp
      }

      def emitCallWithSpread(node: CallExpression) = {
        var target: Expression
        val expr = skipParentheses(node.expression)
        if (expr.kind == SyntaxKind.PropertyAccessExpression) {
          // Target will be emitted as "this" argument
          target = emitCallTarget((<PropertyAccessExpression>expr).expression)
          write(".")
          emit((<PropertyAccessExpression>expr).name)
        }
        else if (expr.kind == SyntaxKind.ElementAccessExpression) {
          // Target will be emitted as "this" argument
          target = emitCallTarget((<PropertyAccessExpression>expr).expression)
          write("[")
          emit((<ElementAccessExpression>expr).argumentExpression)
          write("]")
        }
        else if (expr.kind == SyntaxKind.SuperKeyword) {
          target = expr
          write("_super")
        }
        else {
          emit(node.expression)
        }
        write(".apply(")
        if (target) {
          if (target.kind == SyntaxKind.SuperKeyword) {
            // Calls of form super(...) and super.foo(...)
            emitThis(target)
          }
          else {
            // Calls of form obj.foo(...)
            emit(target)
          }
        }
        else {
          // Calls of form foo(...)
          write("Unit 0")
        }
        write(", ")
        emitListWithSpread(node.arguments, /*needsUniqueCopy*/ false, /*multiLine*/ false, /*trailingComma*/ false, /*useConcat*/ true)
        write(")")
      }

      def isInAsyncMethodWithSuperInES6(node: Node) = {
        if (languageVersion == ScriptTarget.ES6) {
          val container = getSuperContainer(node, /*includeFunctions*/ false)
          if (container && resolver.getNodeCheckFlags(container) & (NodeCheckFlags.AsyncMethodWithSuper | NodeCheckFlags.AsyncMethodWithSuperBinding)) {
            return true
          }
        }

        return false
      }

      def emitSuperAccessInAsyncMethod(superNode: Node, argumentExpression: Expression) = {
        val container = getSuperContainer(superNode, /*includeFunctions*/ false)
        val isSuperBinding = resolver.getNodeCheckFlags(container) & NodeCheckFlags.AsyncMethodWithSuperBinding
        write("_super(")
        emit(argumentExpression)
        write(isSuperBinding ? ").value" : ")")
      }

      def emitCallExpression(node: CallExpression) = {
        if (languageVersion < ScriptTarget.ES6 && hasSpreadElement(node.arguments)) {
          emitCallWithSpread(node)
          return
        }

        val expression = node.expression
        var superCall = false
        var isAsyncMethodWithSuper = false
        if (expression.kind == SyntaxKind.SuperKeyword) {
          emitSuper(expression)
          superCall = true
        }
        else {
          superCall = isSuperPropertyOrElementAccess(expression)
          isAsyncMethodWithSuper = superCall && isInAsyncMethodWithSuperInES6(node)
          emit(expression)
        }

        if (superCall && (languageVersion < ScriptTarget.ES6 || isAsyncMethodWithSuper)) {
          write(".call(")
          emitThis(expression)
          if (node.arguments.length) {
            write(", ")
            emitCommaList(node.arguments)
          }
          write(")")
        }
        else {
          write("(")
          emitCommaList(node.arguments)
          write(")")
        }
      }

      def emitNewExpression(node: NewExpression) = {
        write("new ")

        // Spread operator logic is supported in new expressions in ES5 using a combination
        // of Function.prototype.bind() and Function.prototype.apply().
        //
        //   Example:
        //
        //     var args = [1, 2, 3, 4, 5]
        //     new Array(...args)
        //
        //   is compiled into the following ES5:
        //
        //     var args = [1, 2, 3, 4, 5]
        //     new (Array.bind.apply(Array, [Unit 0].concat(args)))
        //
        // The 'thisArg' to 'bind' is ignored when invoking the result of 'bind' with 'new',
        // Thus, we set it to () ('Unit 0').
        if (languageVersion == ScriptTarget.ES5 &&
          node.arguments &&
          hasSpreadElement(node.arguments)) {

          write("(")
          val target = emitCallTarget(node.expression)
          write(".bind.apply(")
          emit(target)
          write(", [Unit 0].concat(")
          emitListWithSpread(node.arguments, /*needsUniqueCopy*/ false, /*multiLine*/ false, /*trailingComma*/ false, /*useConcat*/ false)
          write(")))")
          write("()")
        }
        else {
          emit(node.expression)
          if (node.arguments) {
            write("(")
            emitCommaList(node.arguments)
            write(")")
          }
        }
      }

      def emitTaggedTemplateExpression(node: TaggedTemplateExpression): Unit = {
        if (languageVersion >= ScriptTarget.ES6) {
          emit(node.tag)
          write(" ")
          emit(node.template)
        }
        else {
          emitDownlevelTaggedTemplate(node)
        }
      }

      def emitParenExpression(node: ParenthesizedExpression) = {
        // If the node is synthesized, it means the emitter put the parentheses there,
        // not the user. If we didn't want them, the emitter would not have put them
        // there.
        if (!nodeIsSynthesized(node) && node.parent.kind != SyntaxKind.ArrowFunction) {
          if (node.expression.kind == SyntaxKind.TypeAssertionExpression || node.expression.kind == SyntaxKind.AsExpression) {
            var operand = (<TypeAssertion>node.expression).expression

            // Make sure we consider all nested cast expressions, e.g.:
            // (<any><Int><any>-A).x
            while (operand.kind == SyntaxKind.TypeAssertionExpression || operand.kind == SyntaxKind.AsExpression) {
              operand = (<TypeAssertion>operand).expression
            }

            // We have an expression of the form: (<Type>SubExpr)
            // Emitting this as (SubExpr) is really not desirable. We would like to emit the subexpr as is.
            // Omitting the parentheses, however, could cause change in the semantics of the generated
            // code if the casted expression has a lower precedence than the rest of the expression, e.g.:
            //    (<any>new A).foo should be emitted as (new A).foo and not new A.foo
            //    (<any>typeof A).toString() should be emitted as (typeof A).toString() and not typeof A.toString()
            //    new (<any>A()) should be emitted as new (A()) and not new A()
            //    (<any>def foo() { })() should be emitted as an IIF (def foo(){})() and not declaration def foo(){} ()
            if (operand.kind != SyntaxKind.PrefixUnaryExpression &&
              operand.kind != SyntaxKind.VoidExpression &&
              operand.kind != SyntaxKind.TypeOfExpression &&
              operand.kind != SyntaxKind.DeleteExpression &&
              operand.kind != SyntaxKind.PostfixUnaryExpression &&
              operand.kind != SyntaxKind.NewExpression &&
              !(operand.kind == SyntaxKind.CallExpression && node.parent.kind == SyntaxKind.NewExpression) &&
              !(operand.kind == SyntaxKind.FunctionExpression && node.parent.kind == SyntaxKind.CallExpression) &&
              !(operand.kind == SyntaxKind.NumericLiteral && node.parent.kind == SyntaxKind.PropertyAccessExpression)) {
              emit(operand)
              return
            }
          }
        }

        write("(")
        emit(node.expression)
        write(")")
      }

      def emitDeleteExpression(node: DeleteExpression) = {
        write(tokenToString(SyntaxKind.DeleteKeyword))
        write(" ")
        emit(node.expression)
      }

      def emitVoidExpression(node: VoidExpression) = {
        write(tokenToString(SyntaxKind.VoidKeyword))
        write(" ")
        emit(node.expression)
      }

      def emitTypeOfExpression(node: TypeOfExpression) = {
        write(tokenToString(SyntaxKind.TypeOfKeyword))
        write(" ")
        emit(node.expression)
      }

      def isNameOfExportedSourceLevelDeclarationInSystemExternalModule(node: Node): Boolean = {
        if (!isCurrentFileSystemExternalModule() || node.kind != SyntaxKind.Identifier || nodeIsSynthesized(node)) {
          return false
        }

        val isVariableDeclarationOrBindingElement =
          node.parent && (node.parent.kind == SyntaxKind.VariableDeclaration || node.parent.kind == SyntaxKind.BindingElement)

        val targetDeclaration =
          isVariableDeclarationOrBindingElement
            ? <Declaration>node.parent
            : resolver.getReferencedValueDeclaration(<Identifier>node)

        return isSourceFileLevelDeclarationInSystemJsModule(targetDeclaration, /*isExported*/ true)
      }

      def emitPrefixUnaryExpression(node: PrefixUnaryExpression) = {
        val exportChanged = (node.operator == SyntaxKind.PlusPlusToken || node.operator == SyntaxKind.MinusMinusToken) &&
          isNameOfExportedSourceLevelDeclarationInSystemExternalModule(node.operand)

        if (exportChanged) {
          // emit
          // ++x
          // as
          // exports('x', ++x)
          write(`${exportFunctionForFile}("`)
          emitNodeWithoutSourceMap(node.operand)
          write(`", `)
        }

        write(tokenToString(node.operator))
        // In some cases, we need to emit a space between the operator and the operand. One obvious case
        // is when the operator is an identifier, like delete or typeof. We also need to do this for plus
        // and minus expressions in certain cases. Specifically, consider the following two cases (parens
        // are just for clarity of exposition, and not part of the source code):
        //
        //  (+(+1))
        //  (+(++1))
        //
        // We need to emit a space in both cases. In the first case, the absence of a space will make
        // the resulting expression a prefix increment operation. And in the second, it will make the resulting
        // expression a prefix increment whose operand is a plus expression - (++(+x))
        // The same is true of minus of course.
        if (node.operand.kind == SyntaxKind.PrefixUnaryExpression) {
          val operand = <PrefixUnaryExpression>node.operand
          if (node.operator == SyntaxKind.PlusToken && (operand.operator == SyntaxKind.PlusToken || operand.operator == SyntaxKind.PlusPlusToken)) {
            write(" ")
          }
          else if (node.operator == SyntaxKind.MinusToken && (operand.operator == SyntaxKind.MinusToken || operand.operator == SyntaxKind.MinusMinusToken)) {
            write(" ")
          }
        }
        emit(node.operand)

        if (exportChanged) {
          write(")")
        }
      }

      def emitPostfixUnaryExpression(node: PostfixUnaryExpression) = {
        val exportChanged = isNameOfExportedSourceLevelDeclarationInSystemExternalModule(node.operand)
        if (exportChanged) {
          // def returns the value that was passes as the second argument
          // however for postfix unary expressions result value should be the value before modification.
          // emit 'x++' as '(export('x', ++x) - 1)' and 'x--' as '(export('x', --x) + 1)'
          write(`(${exportFunctionForFile}("`)
          emitNodeWithoutSourceMap(node.operand)
          write(`", `)

          write(tokenToString(node.operator))
          emit(node.operand)

          if (node.operator == SyntaxKind.PlusPlusToken) {
            write(") - 1)")
          }
          else {
            write(") + 1)")
          }
        }
        else {
          emit(node.operand)
          write(tokenToString(node.operator))
        }
      }

      def shouldHoistDeclarationInSystemJsModule(node: Node): Boolean = {
        return isSourceFileLevelDeclarationInSystemJsModule(node, /*isExported*/ false)
      }

      /*
       * Checks if given node is a source file level declaration (not nested in module/def).
       * If 'isExported' is true - then declaration must also be exported.
       * This def is used in two cases:
       * - check if node is a exported source file level value to determine
       *   if we should also the value after its it changed
       * - check if node is a source level declaration to emit it differently,
       *   i.e non-exported variable statement 'var x = 1' is hoisted so
       *   we we emit variable statement 'var' should be dropped.
       */
      def isSourceFileLevelDeclarationInSystemJsModule(node: Node, isExported: Boolean): Boolean = {
        if (!node || !isCurrentFileSystemExternalModule()) {
          return false
        }

        var current = getRootDeclaration(node).parent
        while (current) {
          if (current.kind == SyntaxKind.SourceFile) {
            return !isExported || ((getCombinedNodeFlags(node) & NodeFlags.Export) != 0)
          }
          else if (isDeclaration(current)) {
            return false
          }
          else {
            current = current.parent
          }
        }
      }

      /**
       * Emit ES7 exponentiation operator downlevel using Math.pow
       * @param node a binary expression node containing exponentiationOperator (**, **=)
       */
      def emitExponentiationOperator(node: BinaryExpression) = {
        val leftHandSideExpression = node.left
        if (node.operatorToken.kind == SyntaxKind.AsteriskAsteriskEqualsToken) {
          var synthesizedLHS: ElementAccessExpression | PropertyAccessExpression
          var shouldEmitParentheses = false
          if (isElementAccessExpression(leftHandSideExpression)) {
            shouldEmitParentheses = true
            write("(")

            synthesizedLHS = <ElementAccessExpression>createSynthesizedNode(SyntaxKind.ElementAccessExpression, /*startsOnNewLine*/ false)

            val identifier = emitTempVariableAssignment(leftHandSideExpression.expression, /*canDefineTempVariablesInPlace*/ false, /*shouldEmitCommaBeforeAssignment*/ false)
            synthesizedLHS.expression = identifier

            if (leftHandSideExpression.argumentExpression.kind != SyntaxKind.NumericLiteral &&
              leftHandSideExpression.argumentExpression.kind != SyntaxKind.StringLiteral) {
              val tempArgumentExpression = createAndRecordTempVariable(TempFlags._i)
              (<ElementAccessExpression>synthesizedLHS).argumentExpression = tempArgumentExpression
              emitAssignment(tempArgumentExpression, leftHandSideExpression.argumentExpression, /*shouldEmitCommaBeforeAssignment*/ true, leftHandSideExpression.expression)
            }
            else {
              (<ElementAccessExpression>synthesizedLHS).argumentExpression = leftHandSideExpression.argumentExpression
            }
            write(", ")
          }
          else if (isPropertyAccessExpression(leftHandSideExpression)) {
            shouldEmitParentheses = true
            write("(")
            synthesizedLHS = <PropertyAccessExpression>createSynthesizedNode(SyntaxKind.PropertyAccessExpression, /*startsOnNewLine*/ false)

            val identifier = emitTempVariableAssignment(leftHandSideExpression.expression, /*canDefineTempVariablesInPlace*/ false, /*shouldEmitCommaBeforeAssignment*/ false)
            synthesizedLHS.expression = identifier

            (<PropertyAccessExpression>synthesizedLHS).dotToken = leftHandSideExpression.dotToken
            (<PropertyAccessExpression>synthesizedLHS).name = leftHandSideExpression.name
            write(", ")
          }

          emit(synthesizedLHS || leftHandSideExpression)
          write(" = ")
          write("Math.pow(")
          emit(synthesizedLHS || leftHandSideExpression)
          write(", ")
          emit(node.right)
          write(")")
          if (shouldEmitParentheses) {
            write(")")
          }
        }
        else {
          write("Math.pow(")
          emit(leftHandSideExpression)
          write(", ")
          emit(node.right)
          write(")")
        }
      }

      def emitBinaryExpression(node: BinaryExpression) = {
        if (languageVersion < ScriptTarget.ES6 && node.operatorToken.kind == SyntaxKind.EqualsToken &&
          (node.left.kind == SyntaxKind.ObjectLiteralExpression || node.left.kind == SyntaxKind.ArrayLiteralExpression)) {
          emitDestructuring(node, node.parent.kind == SyntaxKind.ExpressionStatement)
        }
        else {
          val exportChanged =
            node.operatorToken.kind >= SyntaxKind.FirstAssignment &&
            node.operatorToken.kind <= SyntaxKind.LastAssignment &&
            isNameOfExportedSourceLevelDeclarationInSystemExternalModule(node.left)

          if (exportChanged) {
            // emit assignment 'x <op> y' as 'exports("x", x <op> y)'
            write(`${exportFunctionForFile}("`)
            emitNodeWithoutSourceMap(node.left)
            write(`", `)
          }

          if (node.operatorToken.kind == SyntaxKind.AsteriskAsteriskToken || node.operatorToken.kind == SyntaxKind.AsteriskAsteriskEqualsToken) {
            // Downleveled emit exponentiation operator using Math.pow
            emitExponentiationOperator(node)
          }
          else {
            emit(node.left)
            // Add indentation before emit the operator if the operator is on different line
            // For example:
            //    3
            //    + 2
            //   emitted as
            //    3
            //      + 2
            val indentedBeforeOperator = indentIfOnDifferentLines(node, node.left, node.operatorToken, node.operatorToken.kind != SyntaxKind.CommaToken ? " " : ())
            write(tokenToString(node.operatorToken.kind))
            val indentedAfterOperator = indentIfOnDifferentLines(node, node.operatorToken, node.right, " ")
            emit(node.right)
            decreaseIndentIf(indentedBeforeOperator, indentedAfterOperator)
          }

          if (exportChanged) {
            write(")")
          }
        }
      }

      def synthesizedNodeStartsOnNewLine(node: Node) = {
        return nodeIsSynthesized(node) && (<SynthesizedNode>node).startsOnNewLine
      }

      def emitConditionalExpression(node: ConditionalExpression) = {
        emit(node.condition)
        val indentedBeforeQuestion = indentIfOnDifferentLines(node, node.condition, node.questionToken, " ")
        write("?")
        val indentedAfterQuestion = indentIfOnDifferentLines(node, node.questionToken, node.whenTrue, " ")
        emit(node.whenTrue)
        decreaseIndentIf(indentedBeforeQuestion, indentedAfterQuestion)
        val indentedBeforeColon = indentIfOnDifferentLines(node, node.whenTrue, node.colonToken, " ")
        write(":")
        val indentedAfterColon = indentIfOnDifferentLines(node, node.colonToken, node.whenFalse, " ")
        emit(node.whenFalse)
        decreaseIndentIf(indentedBeforeColon, indentedAfterColon)
      }

      // Helper def to decrease the indent if we previously indented.  Allows multiple
      // previous indent values to be considered at a time.  This also allows caller to just
      // call this once, passing in all their appropriate indent values, instead of needing
      // to call this helper def multiple times.
      def decreaseIndentIf(value1: Boolean, value2?: Boolean) = {
        if (value1) {
          decreaseIndent()
        }
        if (value2) {
          decreaseIndent()
        }
      }

      def isSingleLineEmptyBlock(node: Node) = {
        if (node && node.kind == SyntaxKind.Block) {
          val block = <Block>node
          return block.statements.length == 0 && nodeEndIsOnSameLineAsNodeStart(block, block)
        }
      }

      def emitBlock(node: Block) = {
        if (isSingleLineEmptyBlock(node)) {
          emitToken(SyntaxKind.OpenBraceToken, node.pos)
          write(" ")
          emitToken(SyntaxKind.CloseBraceToken, node.statements.end)
          return
        }

        emitToken(SyntaxKind.OpenBraceToken, node.pos)
        increaseIndent()
        if (node.kind == SyntaxKind.ModuleBlock) {
          Debug.assert(node.parent.kind == SyntaxKind.ModuleDeclaration)
          emitCaptureThisForNodeIfNecessary(node.parent)
        }
        emitLines(node.statements)
        if (node.kind == SyntaxKind.ModuleBlock) {
          emitTempDeclarations(/*newLine*/ true)
        }
        decreaseIndent()
        writeLine()
        emitToken(SyntaxKind.CloseBraceToken, node.statements.end)
      }

      def emitEmbeddedStatement(node: Node) = {
        if (node.kind == SyntaxKind.Block) {
          write(" ")
          emit(<Block>node)
        }
        else {
          increaseIndent()
          writeLine()
          emit(node)
          decreaseIndent()
        }
      }

      def emitExpressionStatement(node: ExpressionStatement) = {
        emitParenthesizedIf(node.expression, /*parenthesized*/ node.expression.kind == SyntaxKind.ArrowFunction)
        write(";")
      }

      def emitIfStatement(node: IfStatement) = {
        var endPos = emitToken(SyntaxKind.IfKeyword, node.pos)
        write(" ")
        endPos = emitToken(SyntaxKind.OpenParenToken, endPos)
        emit(node.expression)
        emitToken(SyntaxKind.CloseParenToken, node.expression.end)
        emitEmbeddedStatement(node.thenStatement)
        if (node.elseStatement) {
          writeLine()
          emitToken(SyntaxKind.ElseKeyword, node.thenStatement.end)
          if (node.elseStatement.kind == SyntaxKind.IfStatement) {
            write(" ")
            emit(node.elseStatement)
          }
          else {
            emitEmbeddedStatement(node.elseStatement)
          }
        }
      }

      def emitDoStatement(node: DoStatement) = {
        emitLoop(node, emitDoStatementWorker)
      }

      def emitDoStatementWorker(node: DoStatement, loop: ConvertedLoop) = {
        write("do")
        if (loop) {
          emitConvertedLoopCall(loop, /*emitAsBlock*/ true)
        }
        else {
          emitNormalLoopBody(node, /*emitAsEmbeddedStatement*/ true)
        }
        if (node.statement.kind == SyntaxKind.Block) {
          write(" ")
        }
        else {
          writeLine()
        }
        write("while (")
        emit(node.expression)
        write(");")
      }

      def emitWhileStatement(node: WhileStatement) = {
        emitLoop(node, emitWhileStatementWorker)
      }

      def emitWhileStatementWorker(node: WhileStatement, loop: ConvertedLoop) = {
        write("while (")
        emit(node.expression)
        write(")")

        if (loop) {
          emitConvertedLoopCall(loop, /*emitAsBlock*/ true)
        }
        else {
          emitNormalLoopBody(node, /*emitAsEmbeddedStatement*/ true)
        }
      }

      /**
       * Returns true if start of variable declaration list was emitted.
       * Returns false if nothing was written - this can happen for source file level variable declarations
       *   in system modules where such variable declarations are hoisted.
       */
      def tryEmitStartOfVariableDeclarationList(decl: VariableDeclarationList): Boolean = {
        if (shouldHoistVariable(decl, /*checkIfSourceFileLevelDecl*/ true)) {
          // variables in variable declaration list were already hoisted
          return false
        }

        if (convertedLoopState && (getCombinedNodeFlags(decl) & NodeFlags.BlockScoped) == 0) {
          // we are inside a converted loop - this can only happen in downlevel scenarios
          // record names for all variable declarations
          for (val varDecl of decl.declarations) {
            hoistVariableDeclarationFromLoop(convertedLoopState, varDecl)
          }
          return false
        }

        emitStart(decl)
        if (decl && languageVersion >= ScriptTarget.ES6) {
          if (isLet(decl)) {
            write("var ")
          }
          else if (isConst(decl)) {
            write("val ")
          }
          else {
            write("var ")
          }
        }
        else {
          write("var ")
        }
        // Note here we specifically dont emit end so that if we are going to emit binding pattern
        // we can alter the source map correctly
        return true
      }

      def emitVariableDeclarationListSkippingUninitializedEntries(list: VariableDeclarationList): Boolean = {
        var started = false
        for (val decl of list.declarations) {
          if (!decl.initializer) {
            continue
          }

          if (!started) {
            started = true
          }
          else {
            write(", ")
          }

          emit(decl)
        }

        return started
      }

      trait ConvertedLoop {
        functionName: String
        paramList: String
        state: ConvertedLoopState
      }

      def shouldConvertLoopBody(node: IterationStatement): Boolean = {
        return languageVersion < ScriptTarget.ES6 &&
          (resolver.getNodeCheckFlags(node) & NodeCheckFlags.LoopWithCapturedBlockScopedBinding) != 0
      }

      def emitLoop(node: IterationStatement, loopEmitter: (n: IterationStatement, convertedLoop: ConvertedLoop) => Unit): Unit = {
        val shouldConvert = shouldConvertLoopBody(node)
        if (!shouldConvert) {
          loopEmitter(node, /* convertedLoop*/ ())
        }
        else {
          val loop = convertLoopBody(node)
          if (node.parent.kind == SyntaxKind.LabeledStatement) {
            // if parent of the loop was labeled statement - attach the label to loop skipping converted loop body
            emitLabelAndColon(<LabeledStatement>node.parent)
          }
          loopEmitter(node, loop)
        }
      }

      def convertLoopBody(node: IterationStatement): ConvertedLoop = {
        val functionName = makeUniqueName("_loop")

        var loopInitializer: VariableDeclarationList
        switch (node.kind) {
          case SyntaxKind.ForStatement:
          case SyntaxKind.ForInStatement:
          case SyntaxKind.ForOfStatement:
            val initializer = (<ForStatement | ForInStatement | ForOfStatement>node).initializer
            if (initializer && initializer.kind == SyntaxKind.VariableDeclarationList) {
              loopInitializer = <VariableDeclarationList>(<ForStatement | ForInStatement | ForOfStatement>node).initializer
            }
            break
        }

        var loopParameters: String[]
        var loopOutParameters: LoopOutParameter[]
        if (loopInitializer && (getCombinedNodeFlags(loopInitializer) & NodeFlags.BlockScoped)) {
          // if loop initializer contains block scoped variables - they should be passed to converted loop body as parameters
          loopParameters = []
          for (val varDeclaration of loopInitializer.declarations) {
            processVariableDeclaration(varDeclaration.name)
          }
        }

        val bodyIsBlock = node.statement.kind == SyntaxKind.Block
        val paramList = loopParameters ? loopParameters.join(", ") : ""

        writeLine()
        write(`var ${functionName} = def(${paramList})`)

        val convertedOuterLoopState = convertedLoopState
        convertedLoopState = { loopOutParameters }

        if (convertedOuterLoopState) {
          // convertedOuterLoopState != () means that this converted loop is nested in another converted loop.
          // if outer converted loop has already accumulated some state - pass it through
          if (convertedOuterLoopState.argumentsName) {
            // outer loop has already used 'arguments' so we've already have some name to alias it
            // use the same name in all nested loops
            convertedLoopState.argumentsName = convertedOuterLoopState.argumentsName
          }

          if (convertedOuterLoopState.thisName) {
            // outer loop has already used 'this' so we've already have some name to alias it
            // use the same name in all nested loops
            convertedLoopState.thisName = convertedOuterLoopState.thisName
          }

          if (convertedOuterLoopState.hoistedLocalVariables) {
            // we've already collected some non-block scoped variable declarations in enclosing loop
            // use the same storage in nested loop
            convertedLoopState.hoistedLocalVariables = convertedOuterLoopState.hoistedLocalVariables
          }
        }

        write(" {")
        writeLine()
        increaseIndent()

        if (bodyIsBlock) {
          emitLines((<Block>node.statement).statements)
        }
        else {
          emit(node.statement)
        }

        writeLine()
        // end of loop body -> copy out parameter
        copyLoopOutParameters(convertedLoopState, CopyDirection.ToOutParameter, /*emitAsStatements*/true)

        decreaseIndent()
        writeLine()
        write("};")
        writeLine()

        if (loopOutParameters) {
          // declare variables to hold out params for loop body
          write(`var `)
          for (var i = 0; i < loopOutParameters.length; i++) {
            if (i != 0) {
              write(", ")
            }
            write(loopOutParameters[i].outParamName)
          }
          write(";")
          writeLine()
        }
        if (convertedLoopState.argumentsName) {
          // if alias for arguments is set
          if (convertedOuterLoopState) {
            // pass it to outer converted loop
            convertedOuterLoopState.argumentsName = convertedLoopState.argumentsName
          }
          else {
            // this is top level converted loop and we need to create an alias for 'arguments' object
            write(`var ${convertedLoopState.argumentsName} = arguments;`)
            writeLine()
          }
        }
        if (convertedLoopState.thisName) {
          // if alias for this is set
          if (convertedOuterLoopState) {
            // pass it to outer converted loop
            convertedOuterLoopState.thisName = convertedLoopState.thisName
          }
          else {
            // this is top level converted loop so we need to create an alias for 'this' here
            // NOTE:
            // if converted loops were all nested in arrow def then we'll always emit '_this' so convertedLoopState.thisName will not be set.
            // If it is set this means that all nested loops are not nested in arrow def and it is safe to capture 'this'.
            write(`var ${convertedLoopState.thisName} = this;`)
            writeLine()
          }
        }

        if (convertedLoopState.hoistedLocalVariables) {
          // if hoistedLocalVariables != () this means that we've possibly collected some variable declarations to be hoisted later
          if (convertedOuterLoopState) {
            // pass them to outer converted loop
            convertedOuterLoopState.hoistedLocalVariables = convertedLoopState.hoistedLocalVariables
          }
          else {
            // deduplicate and hoist collected variable declarations
            write("var ")
            var seen: Map<String>
            for (val id of convertedLoopState.hoistedLocalVariables) {
               // Don't initialize seen unless we have at least one element.
               // Emit a comma to separate for all but the first element.
               if (!seen) {
                 seen = {}
               }
               else {
                 write(", ")
               }

               if (!hasProperty(seen, id.text)) {
                 emit(id)
                 seen[id.text] = id.text
               }
            }
            write(";")
            writeLine()
          }
        }

        val currentLoopState = convertedLoopState
        convertedLoopState = convertedOuterLoopState

        return { functionName, paramList, state: currentLoopState }

        def processVariableDeclaration(name: Identifier | BindingPattern): Unit = {
          if (name.kind == SyntaxKind.Identifier) {
            val nameText = isNameOfNestedBlockScopedRedeclarationOrCapturedBinding(<Identifier>name)
              ? getGeneratedNameForNode(name)
              : (<Identifier>name).text

            loopParameters.push(nameText)
            if (resolver.getNodeCheckFlags(name.parent) & NodeCheckFlags.NeedsLoopOutParameter) {
              val reassignedVariable = { originalName: <Identifier>name, outParamName: makeUniqueName(`out_${nameText}`) }
              (loopOutParameters || (loopOutParameters = [])).push(reassignedVariable)
            }
          }
          else {
            for (val element of (<BindingPattern>name).elements) {
              processVariableDeclaration(element.name)
            }
          }
        }
      }

      def emitNormalLoopBody(node: IterationStatement, emitAsEmbeddedStatement: Boolean): Unit = {
        var saveAllowedNonLabeledJumps: Jump
        if (convertedLoopState) {
          // we get here if we are trying to emit normal loop loop inside converted loop
          // set allowedNonLabeledJumps to Break | Continue to mark that break\continue inside the loop should be emitted as is
          saveAllowedNonLabeledJumps = convertedLoopState.allowedNonLabeledJumps
          convertedLoopState.allowedNonLabeledJumps = Jump.Break | Jump.Continue
        }

        if (emitAsEmbeddedStatement) {
          emitEmbeddedStatement(node.statement)
        }
        else if (node.statement.kind == SyntaxKind.Block) {
          emitLines((<Block>node.statement).statements)
        }
        else {
          writeLine()
          emit(node.statement)
        }

        if (convertedLoopState) {
          convertedLoopState.allowedNonLabeledJumps = saveAllowedNonLabeledJumps
        }
      }

      def copyLoopOutParameters(state: ConvertedLoopState, copyDirection: CopyDirection, emitAsStatements: Boolean) = {
        if (state.loopOutParameters) {
          for (val outParam of state.loopOutParameters) {
            if (copyDirection == CopyDirection.ToOriginal) {
              emitIdentifier(outParam.originalName)
              write(` = ${outParam.outParamName}`)
            }
            else {
              write(`${outParam.outParamName} = `)
              emitIdentifier(outParam.originalName)
            }
            if (emitAsStatements) {
              write(";")
              writeLine()
            }
            else {
              write(", ")
            }
          }
        }
      }

      def emitConvertedLoopCall(loop: ConvertedLoop, emitAsBlock: Boolean): Unit = {
        if (emitAsBlock) {
          write(" {")
          writeLine()
          increaseIndent()
        }

        // loop is considered simple if it does not have any return statements or break\continue that transfer control outside of the loop
        // simple loops are emitted as just 'loop()'
        val isSimpleLoop =
          !loop.state.nonLocalJumps &&
          !loop.state.labeledNonLocalBreaks &&
          !loop.state.labeledNonLocalContinues

        val loopResult = makeUniqueName("state")
        if (!isSimpleLoop) {
          write(`var ${loopResult} = `)
        }

        write(`${loop.functionName}(${loop.paramList});`)
        writeLine()

        copyLoopOutParameters(loop.state, CopyDirection.ToOriginal, /*emitAsStatements*/ true)

        if (!isSimpleLoop) {
          // for non simple loops we need to store result returned from converted loop def and use it to do dispatching
          // converted loop def can return:
          // - object - used when body of the converted loop contains return statement. Property "value" of this object stores retuned value
          // - String - used to dispatch jumps. "break" and "continue" are used to non-labeled jumps, other values are used to transfer control to
          //   different labels
          writeLine()
          if (loop.state.nonLocalJumps & Jump.Return) {
            write(`if (typeof ${loopResult} == "object") `)
            if (convertedLoopState) {
              // we are currently nested in another converted loop - return unwrapped result
              write(`return ${loopResult};`)
              // propagate 'hasReturn' flag to outer loop
              convertedLoopState.nonLocalJumps |= Jump.Return
            }
            else {
              // top level converted loop - return unwrapped value
              write(`return ${loopResult}.value;`)
            }
            writeLine()
          }

          if (loop.state.nonLocalJumps & Jump.Break) {
            write(`if (${loopResult} == "break") break;`)
            writeLine()
          }

          if (loop.state.nonLocalJumps & Jump.Continue) {
            write(`if (${loopResult} == "continue") continue;`)
            writeLine()
          }

          // in case of labeled breaks emit code that either breaks to some known label inside outer loop or delegates jump decision to outer loop
          emitDispatchTableForLabeledJumps(loopResult, loop.state, convertedLoopState)
        }

        if (emitAsBlock) {
          writeLine()
          decreaseIndent()
          write("}")
        }

        def emitDispatchTableForLabeledJumps(loopResultVariable: String, currentLoop: ConvertedLoopState, outerLoop: ConvertedLoopState) = {
          if (!currentLoop.labeledNonLocalBreaks && !currentLoop.labeledNonLocalContinues) {
            return
          }

          write(`switch(${loopResultVariable}) {`)
          increaseIndent()

          emitDispatchEntriesForLabeledJumps(currentLoop.labeledNonLocalBreaks, /*isBreak*/ true, loopResultVariable, outerLoop)
          emitDispatchEntriesForLabeledJumps(currentLoop.labeledNonLocalContinues, /*isBreak*/ false, loopResultVariable, outerLoop)

          decreaseIndent()
          writeLine()
          write("}")
        }

        def emitDispatchEntriesForLabeledJumps(table: Map<String>, isBreak: Boolean, loopResultVariable: String, outerLoop: ConvertedLoopState): Unit = {
          if (!table) {
            return
          }

          for (val labelText in table) {
            val labelMarker = table[labelText]
            writeLine()
            write(`case "${labelMarker}": `)
            // if there are no outer converted loop or outer label in question is located inside outer converted loop
            // then emit labeled break\continue
            // otherwise propagate pair 'label -> marker' to outer converted loop and emit 'return labelMarker' so outer loop can later decide what to do
            if (!outerLoop || (outerLoop.labels && outerLoop.labels[labelText])) {
              if (isBreak) {
                write("break ")
              }
              else {
                write("continue ")
              }
              write(`${labelText};`)
            }
            else {
              setLabeledJump(outerLoop, isBreak, labelText, labelMarker)
              write(`return ${loopResultVariable};`)
            }
          }
        }
      }

      def emitForStatement(node: ForStatement) = {
        emitLoop(node, emitForStatementWorker)
      }

      def emitForStatementWorker(node: ForStatement, loop: ConvertedLoop) = {
        var endPos = emitToken(SyntaxKind.ForKeyword, node.pos)
        write(" ")
        endPos = emitToken(SyntaxKind.OpenParenToken, endPos)
        if (node.initializer && node.initializer.kind == SyntaxKind.VariableDeclarationList) {
          val variableDeclarationList = <VariableDeclarationList>node.initializer
          val startIsEmitted = tryEmitStartOfVariableDeclarationList(variableDeclarationList)
          if (startIsEmitted) {
            emitCommaList(variableDeclarationList.declarations)
          }
          else {
            emitVariableDeclarationListSkippingUninitializedEntries(variableDeclarationList)
          }
        }
        else if (node.initializer) {
          emit(node.initializer)
        }
        write(";")
        emitOptional(" ", node.condition)
        write(";")
        emitOptional(" ", node.incrementor)
        write(")")

        if (loop) {
          emitConvertedLoopCall(loop, /*emitAsBlock*/ true)
        }
        else {
          emitNormalLoopBody(node, /*emitAsEmbeddedStatement*/ true)
        }
      }

      def emitForInOrForOfStatement(node: ForInStatement | ForOfStatement) = {
        if (languageVersion < ScriptTarget.ES6 && node.kind == SyntaxKind.ForOfStatement) {
          emitLoop(node, emitDownLevelForOfStatementWorker)
        }
        else {
          emitLoop(node, emitForInOrForOfStatementWorker)
        }
      }

      def emitForInOrForOfStatementWorker(node: ForInStatement | ForOfStatement, loop: ConvertedLoop) = {
        var endPos = emitToken(SyntaxKind.ForKeyword, node.pos)
        write(" ")
        endPos = emitToken(SyntaxKind.OpenParenToken, endPos)
        if (node.initializer.kind == SyntaxKind.VariableDeclarationList) {
          val variableDeclarationList = <VariableDeclarationList>node.initializer
          if (variableDeclarationList.declarations.length >= 1) {
            tryEmitStartOfVariableDeclarationList(variableDeclarationList)
            emit(variableDeclarationList.declarations[0])
          }
        }
        else {
          emit(node.initializer)
        }

        if (node.kind == SyntaxKind.ForInStatement) {
          write(" in ")
        }
        else {
          write(" of ")
        }
        emit(node.expression)
        emitToken(SyntaxKind.CloseParenToken, node.expression.end)

        if (loop) {
          emitConvertedLoopCall(loop, /*emitAsBlock*/ true)
        }
        else {
          emitNormalLoopBody(node, /*emitAsEmbeddedStatement*/ true)
        }
      }

      def emitDownLevelForOfStatementWorker(node: ForOfStatement, loop: ConvertedLoop) = {
        // The following ES6 code:
        //
        //  for (var v of expr) { }
        //
        // should be emitted as
        //
        //  for (var _i = 0, _a = expr; _i < _a.length; _i++) {
        //    var v = _a[_i]
        //  }
        //
        // where _a and _i are temps emitted to capture the RHS and the counter,
        // respectively.
        // When the left hand side is an expression instead of a var declaration,
        // the "var v" is not emitted.
        // When the left hand side is a var/val, the v is renamed if there is
        // another v in scope.
        // Note that all assignments to the LHS are emitted in the body, including
        // all destructuring.
        // Note also that because an extra statement is needed to assign to the LHS,
        // for-of bodies are always emitted as blocks.

        var endPos = emitToken(SyntaxKind.ForKeyword, node.pos)
        write(" ")
        endPos = emitToken(SyntaxKind.OpenParenToken, endPos)

        // Do not emit the LHS var declaration yet, because it might contain destructuring.

        // Do not call recordTempDeclaration because we are declaring the temps
        // right here. Recording means they will be declared later.
        // In the case where the user wrote an identifier as the RHS, like this:
        //
        //   for (var v of arr) { }
        //
        // we can't reuse 'arr' because it might be modified within the body of the loop.
        val counter = createTempVariable(TempFlags._i)
        val rhsReference = createSynthesizedNode(SyntaxKind.Identifier) as Identifier
        rhsReference.text = node.expression.kind == SyntaxKind.Identifier ?
          makeUniqueName((<Identifier>node.expression).text) :
          makeTempVariableName(TempFlags.Auto)

        // This is the var keyword for the counter and rhsReference. The var keyword for
        // the LHS will be emitted inside the body.
        emitStart(node.expression)
        write("var ")

        // _i = 0
        emitNodeWithoutSourceMap(counter)
        write(" = 0")
        emitEnd(node.expression)

        // , _a = expr
        write(", ")
        emitStart(node.expression)
        emitNodeWithoutSourceMap(rhsReference)
        write(" = ")
        emitNodeWithoutSourceMap(node.expression)
        emitEnd(node.expression)

        write("; ")

        // _i < _a.length
        emitStart(node.expression)
        emitNodeWithoutSourceMap(counter)
        write(" < ")

        emitNodeWithCommentsAndWithoutSourcemap(rhsReference)
        write(".length")

        emitEnd(node.expression)
        write("; ")

        // _i++)
        emitStart(node.expression)
        emitNodeWithoutSourceMap(counter)
        write("++")
        emitEnd(node.expression)
        emitToken(SyntaxKind.CloseParenToken, node.expression.end)

        // Body
        write(" {")
        writeLine()
        increaseIndent()

        // Initialize LHS
        // var v = _a[_i]
        val rhsIterationValue = createElementAccessExpression(rhsReference, counter)
        emitStart(node.initializer)
        if (node.initializer.kind == SyntaxKind.VariableDeclarationList) {
          write("var ")
          val variableDeclarationList = <VariableDeclarationList>node.initializer
          if (variableDeclarationList.declarations.length > 0) {
            val declaration = variableDeclarationList.declarations[0]
            if (isBindingPattern(declaration.name)) {
              // This works whether the declaration is a var, var, or val.
              // It will use rhsIterationValue _a[_i] as the initializer.
              emitDestructuring(declaration, /*isAssignmentExpressionStatement*/ false, rhsIterationValue)
            }
            else {
              // The following call does not include the initializer, so we have
              // to emit it separately.
              emitNodeWithCommentsAndWithoutSourcemap(declaration)
              write(" = ")
              emitNodeWithoutSourceMap(rhsIterationValue)
            }
          }
          else {
            // It's an empty declaration list. This can only happen in an error case, if the user wrote
            //   for (var of []) {}
            emitNodeWithoutSourceMap(createTempVariable(TempFlags.Auto))
            write(" = ")
            emitNodeWithoutSourceMap(rhsIterationValue)
          }
        }
        else {
          // Initializer is an expression. Emit the expression in the body, so that it's
          // evaluated on every iteration.
          val assignmentExpression = createBinaryExpression(<Expression>node.initializer, SyntaxKind.EqualsToken, rhsIterationValue, /*startsOnNewLine*/ false)
          if (node.initializer.kind == SyntaxKind.ArrayLiteralExpression || node.initializer.kind == SyntaxKind.ObjectLiteralExpression) {
            // This is a destructuring pattern, so call emitDestructuring instead of emit. Calling emit will not work, because it will cause
            // the BinaryExpression to be passed in instead of the expression statement, which will cause emitDestructuring to crash.
            emitDestructuring(assignmentExpression, /*isAssignmentExpressionStatement*/ true, /*value*/ ())
          }
          else {
            emitNodeWithCommentsAndWithoutSourcemap(assignmentExpression)
          }
        }
        emitEnd(node.initializer)
        write(";")

        if (loop) {
          writeLine()
          emitConvertedLoopCall(loop, /*emitAsBlock*/ false)
        }
        else {
          emitNormalLoopBody(node, /*emitAsEmbeddedStatement*/ false)
        }

        writeLine()
        decreaseIndent()
        write("}")
      }

      def emitBreakOrContinueStatement(node: BreakOrContinueStatement) = {
        if (convertedLoopState) {
          // check if we can emit break\continue as is
          // it is possible if either
          //   - break\continue is statement labeled and label is located inside the converted loop
          //   - break\continue is non-labeled and located in non-converted loop\switch statement
          val jump = node.kind == SyntaxKind.BreakStatement ? Jump.Break : Jump.Continue
          val canUseBreakOrContinue =
            (node.label && convertedLoopState.labels && convertedLoopState.labels[node.label.text]) ||
            (!node.label && (convertedLoopState.allowedNonLabeledJumps & jump))

          if (!canUseBreakOrContinue) {
            write ("return ")
            // explicit exit from loop -> copy out parameters
            copyLoopOutParameters(convertedLoopState, CopyDirection.ToOutParameter, /*emitAsStatements*/ false)
            if (!node.label) {
              if (node.kind == SyntaxKind.BreakStatement) {
                convertedLoopState.nonLocalJumps |= Jump.Break
                write(`"break";`)
              }
              else {
                convertedLoopState.nonLocalJumps |= Jump.Continue
                write(`"continue";`)
              }
            }
            else {
              var labelMarker: String
              if (node.kind == SyntaxKind.BreakStatement) {
                labelMarker = `break-${node.label.text}`
                setLabeledJump(convertedLoopState, /*isBreak*/ true, node.label.text, labelMarker)
              }
              else {
                labelMarker = `continue-${node.label.text}`
                setLabeledJump(convertedLoopState, /*isBreak*/ false, node.label.text, labelMarker)
              }
              write(`"${labelMarker}";`)
            }

            return
          }
        }

        emitToken(node.kind == SyntaxKind.BreakStatement ? SyntaxKind.BreakKeyword : SyntaxKind.ContinueKeyword, node.pos)
        emitOptional(" ", node.label)
        write(";")
      }

      def emitReturnStatement(node: ReturnStatement) = {
        if (convertedLoopState) {
          convertedLoopState.nonLocalJumps |= Jump.Return
          write("return { value: ")
          if (node.expression) {
            emit(node.expression)
          }
          else {
            write("Unit 0")
          }
          write(" };")
          return
        }

        emitToken(SyntaxKind.ReturnKeyword, node.pos)
        emitOptional(" ", node.expression)
        write(";")
      }

      def emitWithStatement(node: WithStatement) = {
        write("with (")
        emit(node.expression)
        write(")")
        emitEmbeddedStatement(node.statement)
      }

      def emitSwitchStatement(node: SwitchStatement) = {
        var endPos = emitToken(SyntaxKind.SwitchKeyword, node.pos)
        write(" ")
        emitToken(SyntaxKind.OpenParenToken, endPos)
        emit(node.expression)
        endPos = emitToken(SyntaxKind.CloseParenToken, node.expression.end)
        write(" ")

        var saveAllowedNonLabeledJumps: Jump
        if (convertedLoopState) {
          saveAllowedNonLabeledJumps = convertedLoopState.allowedNonLabeledJumps
          // for switch statement allow only non-labeled break
          convertedLoopState.allowedNonLabeledJumps |= Jump.Break
        }
        emitCaseBlock(node.caseBlock, endPos)
        if (convertedLoopState) {
          convertedLoopState.allowedNonLabeledJumps = saveAllowedNonLabeledJumps
        }
      }

      def emitCaseBlock(node: CaseBlock, startPos: Int): Unit = {
        emitToken(SyntaxKind.OpenBraceToken, startPos)
        increaseIndent()
        emitLines(node.clauses)
        decreaseIndent()
        writeLine()
        emitToken(SyntaxKind.CloseBraceToken, node.clauses.end)
      }

      def nodeStartPositionsAreOnSameLine(node1: Node, node2: Node) = {
        return getLineOfLocalPositionFromLineMap(currentLineMap, skipTrivia(currentText, node1.pos)) ==
          getLineOfLocalPositionFromLineMap(currentLineMap, skipTrivia(currentText, node2.pos))
      }

      def nodeEndPositionsAreOnSameLine(node1: Node, node2: Node) = {
        return getLineOfLocalPositionFromLineMap(currentLineMap, node1.end) ==
          getLineOfLocalPositionFromLineMap(currentLineMap, node2.end)
      }

      def nodeEndIsOnSameLineAsNodeStart(node1: Node, node2: Node) = {
        return getLineOfLocalPositionFromLineMap(currentLineMap, node1.end) ==
          getLineOfLocalPositionFromLineMap(currentLineMap, skipTrivia(currentText, node2.pos))
      }

      def emitCaseOrDefaultClause(node: CaseOrDefaultClause) = {
        if (node.kind == SyntaxKind.CaseClause) {
          write("case ")
          emit((<CaseClause>node).expression)
          write(":")
        }
        else {
          write("default:")
        }

        if (node.statements.length == 1 && nodeStartPositionsAreOnSameLine(node, node.statements[0])) {
          write(" ")
          emit(node.statements[0])
        }
        else {
          increaseIndent()
          emitLines(node.statements)
          decreaseIndent()
        }
      }

      def emitThrowStatement(node: ThrowStatement) = {
        write("throw ")
        emit(node.expression)
        write(";")
      }

      def emitTryStatement(node: TryStatement) = {
        write("try ")
        emit(node.tryBlock)
        emit(node.catchClause)
        if (node.finallyBlock) {
          writeLine()
          write("finally ")
          emit(node.finallyBlock)
        }
      }

      def emitCatchClause(node: CatchClause) = {
        writeLine()
        val endPos = emitToken(SyntaxKind.CatchKeyword, node.pos)
        write(" ")
        emitToken(SyntaxKind.OpenParenToken, endPos)
        emit(node.variableDeclaration)
        emitToken(SyntaxKind.CloseParenToken, node.variableDeclaration ? node.variableDeclaration.end : endPos)
        write(" ")
        emitBlock(node.block)
      }

      def emitDebuggerStatement(node: Node) = {
        emitToken(SyntaxKind.DebuggerKeyword, node.pos)
        write(";")
      }

      def emitLabelAndColon(node: LabeledStatement): Unit = {
        emit(node.label)
        write(": ")
      }

      def emitLabeledStatement(node: LabeledStatement) = {
        if (!isIterationStatement(node.statement, /* lookInLabeledStatements */ false) || !shouldConvertLoopBody(<IterationStatement>node.statement)) {
          emitLabelAndColon(node)
        }

        if (convertedLoopState) {
          if (!convertedLoopState.labels) {
            convertedLoopState.labels = {}
          }
          convertedLoopState.labels[node.label.text] = node.label.text
        }

        emit(node.statement)

        if (convertedLoopState) {
          convertedLoopState.labels[node.label.text] = ()
        }
      }

      def getContainingModule(node: Node): ModuleDeclaration = {
        do {
          node = node.parent
        } while (node && node.kind != SyntaxKind.ModuleDeclaration)
        return <ModuleDeclaration>node
      }

      def emitContainingModuleName(node: Node) = {
        val container = getContainingModule(node)
        write(container ? getGeneratedNameForNode(container) : "exports")
      }

      def emitModuleMemberName(node: Declaration) = {
        emitStart(node.name)
        if (getCombinedNodeFlags(node) & NodeFlags.Export) {
          val container = getContainingModule(node)
          if (container) {
            write(getGeneratedNameForNode(container))
            write(".")
          }
          else if (modulekind != ModuleKind.ES6 && modulekind != ModuleKind.System) {
            write("exports.")
          }
        }
        emitNodeWithCommentsAndWithoutSourcemap(node.name)
        emitEnd(node.name)
      }

      def createVoidZero(): Expression = {
        val zero = <LiteralExpression>createSynthesizedNode(SyntaxKind.NumericLiteral)
        zero.text = "0"
        val result = <VoidExpression>createSynthesizedNode(SyntaxKind.VoidExpression)
        result.expression = zero
        return result
      }

      def emitEs6ExportDefaultCompat(node: Node) = {
        if (node.parent.kind == SyntaxKind.SourceFile) {
          Debug.assert(!!(node.flags & NodeFlags.Default) || node.kind == SyntaxKind.ExportAssignment)
          // only allow default at a source file level
          if (modulekind == ModuleKind.CommonJS || modulekind == ModuleKind.AMD || modulekind == ModuleKind.UMD) {
            if (!isEs6Module) {
              if (languageVersion != ScriptTarget.ES3) {
                // default value of configurable, enumerable, writable are `false`.
                write("Object.defineProperty(exports, \"__esModule\", { value: true });")
                writeLine()
              }
              else {
                write("exports.__esModule = true;")
                writeLine()
              }
            }
          }
        }
      }

      def emitExportMemberAssignment(node: FunctionLikeDeclaration | ClassDeclaration) = {
        if (node.flags & NodeFlags.Export) {
          writeLine()
          emitStart(node)

          // emit call to exporter only for top level nodes
          if (modulekind == ModuleKind.System && node.parent == currentSourceFile) {
            // emit default <smth> as
            // export("default", <smth>)
            write(`${exportFunctionForFile}("`)
            if (node.flags & NodeFlags.Default) {
              write("default")
            }
            else {
              emitNodeWithCommentsAndWithoutSourcemap(node.name)
            }
            write(`", `)
            emitDeclarationName(node)
            write(")")
          }
          else {
            if (node.flags & NodeFlags.Default) {
              emitEs6ExportDefaultCompat(node)
              if (languageVersion == ScriptTarget.ES3) {
                write("exports[\"default\"]")
              }
              else {
                write("exports.default")
              }
            }
            else {
              emitModuleMemberName(node)
            }
            write(" = ")
            emitDeclarationName(node)
          }
          emitEnd(node)
          write(";")
        }
      }

      def emitExportMemberAssignments(name: Identifier) = {
        if (modulekind == ModuleKind.System) {
          return
        }

        if (!exportEquals && exportSpecifiers && hasProperty(exportSpecifiers, name.text)) {
          for (val specifier of exportSpecifiers[name.text]) {
            writeLine()
            emitStart(specifier.name)
            emitContainingModuleName(specifier)
            write(".")
            emitNodeWithCommentsAndWithoutSourcemap(specifier.name)
            emitEnd(specifier.name)
            write(" = ")
            emitExpressionIdentifier(name)
            write(";")
          }
        }
      }

      def emitExportSpecifierInSystemModule(specifier: ExportSpecifier): Unit = {
        Debug.assert(modulekind == ModuleKind.System)

        if (!resolver.getReferencedValueDeclaration(specifier.propertyName || specifier.name) && !resolver.isValueAliasDeclaration(specifier) ) {
          return
        }

        writeLine()
        emitStart(specifier.name)
        write(`${exportFunctionForFile}("`)
        emitNodeWithCommentsAndWithoutSourcemap(specifier.name)
        write(`", `)
        emitExpressionIdentifier(specifier.propertyName || specifier.name)
        write(")")
        emitEnd(specifier.name)
        write(";")
      }

      /**
       * Emit an assignment to a given identifier, 'name', with a given expression, 'value'.
       * @param name an identifier as a left-hand-side operand of the assignment
       * @param value an expression as a right-hand-side operand of the assignment
       * @param shouldEmitCommaBeforeAssignment a Boolean indicating whether to prefix an assignment with comma
       */
      def emitAssignment(name: Identifier, value: Expression, shouldEmitCommaBeforeAssignment: Boolean, nodeForSourceMap: Node) = {
        if (shouldEmitCommaBeforeAssignment) {
          write(", ")
        }

        val exportChanged = isNameOfExportedSourceLevelDeclarationInSystemExternalModule(name)

        if (exportChanged) {
          write(`${exportFunctionForFile}("`)
          emitNodeWithCommentsAndWithoutSourcemap(name)
          write(`", `)
        }

        val isVariableDeclarationOrBindingElement =
          name.parent && (name.parent.kind == SyntaxKind.VariableDeclaration || name.parent.kind == SyntaxKind.BindingElement)

        // If this is first var declaration, we need to start at var/var/val keyword instead
        // otherwise use nodeForSourceMap as the start position
        emitStart(isFirstVariableDeclaration(nodeForSourceMap) ? nodeForSourceMap.parent : nodeForSourceMap)
        withTemporaryNoSourceMap(() => {
          if (isVariableDeclarationOrBindingElement) {
            emitModuleMemberName(<Declaration>name.parent)
          }
          else {
            emit(name)
          }

          write(" = ")
          emit(value)
        })
        emitEnd(nodeForSourceMap, /*stopOverridingSpan*/true)

        if (exportChanged) {
          write(")")
        }
      }

      /**
       * Create temporary variable, emit an assignment of the variable the given expression
       * @param expression an expression to assign to the newly created temporary variable
       * @param canDefineTempVariablesInPlace a Boolean indicating whether you can define the temporary variable at an assignment location
       * @param shouldEmitCommaBeforeAssignment a Boolean indicating whether an assignment should prefix with comma
       */
      def emitTempVariableAssignment(expression: Expression, canDefineTempVariablesInPlace: Boolean, shouldEmitCommaBeforeAssignment: Boolean, sourceMapNode?: Node): Identifier = {
        val identifier = createTempVariable(TempFlags.Auto)
        if (!canDefineTempVariablesInPlace) {
          recordTempDeclaration(identifier)
        }
        emitAssignment(identifier, expression, shouldEmitCommaBeforeAssignment, sourceMapNode || expression.parent)
        return identifier
      }

      def isFirstVariableDeclaration(root: Node) = {
        return root.kind == SyntaxKind.VariableDeclaration &&
          root.parent.kind == SyntaxKind.VariableDeclarationList &&
          (<VariableDeclarationList>root.parent).declarations[0] == root
      }

      def emitDestructuring(root: BinaryExpression | VariableDeclaration | ParameterDeclaration, isAssignmentExpressionStatement: Boolean, value?: Expression) = {
        var emitCount = 0

        // An exported declaration is actually emitted as an assignment (to a property on the module object), so
        // temporary variables in an exported declaration need to have real declarations elsewhere
        // Also temporary variables should be explicitly allocated for source level declarations when module target is system
        // because actual variable declarations are hoisted
        var canDefineTempVariablesInPlace = false
        if (root.kind == SyntaxKind.VariableDeclaration) {
          val isExported = getCombinedNodeFlags(root) & NodeFlags.Export
          val isSourceLevelForSystemModuleKind = shouldHoistDeclarationInSystemJsModule(root)
          canDefineTempVariablesInPlace = !isExported && !isSourceLevelForSystemModuleKind
        }
        else if (root.kind == SyntaxKind.Parameter) {
          canDefineTempVariablesInPlace = true
        }

        if (root.kind == SyntaxKind.BinaryExpression) {
          emitAssignmentExpression(<BinaryExpression>root)
        }
        else {
          Debug.assert(!isAssignmentExpressionStatement)
          // If first variable declaration of variable statement correct the start location
          if (isFirstVariableDeclaration(root)) {
            // Use emit location of "var " as next emit start entry
            sourceMap.changeEmitSourcePos()
          }
          emitBindingElement(<BindingElement>root, value)
        }


        /**
         * Ensures that there exists a declared identifier whose value holds the given expression.
         * This def is useful to ensure that the expression's value can be read from in subsequent expressions.
         * Unless 'reuseIdentifierExpressions' is false, 'expr' will be returned if it is just an identifier.
         *
         * @param expr the expression whose value needs to be bound.
         * @param reuseIdentifierExpressions true if identifier expressions can simply be returned
         *                   false if it is necessary to always emit an identifier.
         */
        def ensureIdentifier(expr: Expression, reuseIdentifierExpressions: Boolean, sourceMapNode: Node): Expression = {
          if (expr.kind == SyntaxKind.Identifier && reuseIdentifierExpressions) {
            return expr
          }

          val identifier = emitTempVariableAssignment(expr, canDefineTempVariablesInPlace, emitCount > 0, sourceMapNode)
          emitCount++
          return identifier
        }

        def createDefaultValueCheck(value: Expression, defaultValue: Expression, sourceMapNode: Node): Expression = {
          // The value expression will be evaluated twice, so for anything but a simple identifier
          // we need to generate a temporary variable
          // If the temporary variable needs to be emitted use the source Map node for assignment of that statement
          value = ensureIdentifier(value, /*reuseIdentifierExpressions*/ true, sourceMapNode)
          // Return the expression 'value == Unit 0 ? defaultValue : value'
          val equals = <BinaryExpression>createSynthesizedNode(SyntaxKind.BinaryExpression)
          equals.left = value
          equals.operatorToken = createSynthesizedNode(SyntaxKind.EqualsEqualsEqualsToken)
          equals.right = createVoidZero()
          return createConditionalExpression(equals, defaultValue, value)
        }

        def createConditionalExpression(condition: Expression, whenTrue: Expression, whenFalse: Expression) = {
          val cond = <ConditionalExpression>createSynthesizedNode(SyntaxKind.ConditionalExpression)
          cond.condition = condition
          cond.questionToken = createSynthesizedNode(SyntaxKind.QuestionToken)
          cond.whenTrue = whenTrue
          cond.colonToken = createSynthesizedNode(SyntaxKind.ColonToken)
          cond.whenFalse = whenFalse
          return cond
        }

        def createNumericLiteral(value: Int) = {
          val node = <LiteralExpression>createSynthesizedNode(SyntaxKind.NumericLiteral)
          node.text = "" + value
          return node
        }

        def createPropertyAccessForDestructuringProperty(object: Expression, propName: PropertyName): Expression = {
          var index: Expression
          val nameIsComputed = propName.kind == SyntaxKind.ComputedPropertyName
          if (nameIsComputed) {
            // TODO to handle when we look into sourcemaps for computed properties, for now use propName
            index = ensureIdentifier((<ComputedPropertyName>propName).expression, /*reuseIdentifierExpressions*/ false, propName)
          }
          else {
            // We create a synthetic copy of the identifier in order to avoid the rewriting that might
            // otherwise occur when the identifier is emitted.
            index = <Identifier | LiteralExpression>createSynthesizedNode(propName.kind)
            // We need to unescape identifier here because when parsing an identifier prefixing with "__"
            // the parser need to append "_" in order to escape colliding with magic identifiers such as "__proto__"
            // Therefore, in order to correctly emit identifiers that are written in original TypeScript file,
            // we will unescapeIdentifier to remove additional underscore (if no underscore is added, the def will return original input String)
            (<Identifier | LiteralExpression>index).text = unescapeIdentifier((<Identifier | LiteralExpression>propName).text)
          }

          return !nameIsComputed && index.kind == SyntaxKind.Identifier
            ? createPropertyAccessExpression(object, <Identifier>index)
            : createElementAccessExpression(object, index)
        }

        def createSliceCall(value: Expression, sliceIndex: Int): CallExpression = {
          val call = <CallExpression>createSynthesizedNode(SyntaxKind.CallExpression)
          val sliceIdentifier = <Identifier>createSynthesizedNode(SyntaxKind.Identifier)
          sliceIdentifier.text = "slice"
          call.expression = createPropertyAccessExpression(value, sliceIdentifier)
          call.arguments = <NodeArray<LiteralExpression>>createSynthesizedNodeArray()
          call.arguments[0] = createNumericLiteral(sliceIndex)
          return call
        }

        def emitObjectLiteralAssignment(target: ObjectLiteralExpression, value: Expression, sourceMapNode: Node) = {
          val properties = target.properties
          if (properties.length != 1) {
            // For anything but a single element destructuring we need to generate a temporary
            // to ensure value is evaluated exactly once.
            // When doing so we want to highlight the passed in source map node since thats the one needing this temp assignment
            value = ensureIdentifier(value, /*reuseIdentifierExpressions*/ true, sourceMapNode)
          }
          for (val p of properties) {
            if (p.kind == SyntaxKind.PropertyAssignment || p.kind == SyntaxKind.ShorthandPropertyAssignment) {
              val propName = <Identifier | LiteralExpression>(<PropertyAssignment>p).name
              val target = p.kind == SyntaxKind.ShorthandPropertyAssignment ? <ShorthandPropertyAssignment>p : (<PropertyAssignment>p).initializer || propName
              // Assignment for target = value.propName should highlight whole property, hence use p as source map node
              emitDestructuringAssignment(target, createPropertyAccessForDestructuringProperty(value, propName), p)
            }
          }
        }

        def emitArrayLiteralAssignment(target: ArrayLiteralExpression, value: Expression, sourceMapNode: Node) = {
          val elements = target.elements
          if (elements.length != 1) {
            // For anything but a single element destructuring we need to generate a temporary
            // to ensure value is evaluated exactly once.
            // When doing so we want to highlight the passed in source map node since thats the one needing this temp assignment
            value = ensureIdentifier(value, /*reuseIdentifierExpressions*/ true, sourceMapNode)
          }
          for (var i = 0; i < elements.length; i++) {
            val e = elements[i]
            if (e.kind != SyntaxKind.OmittedExpression) {
              // Assignment for target = value.propName should highlight whole property, hence use e as source map node
              if (e.kind != SyntaxKind.SpreadElementExpression) {
                emitDestructuringAssignment(e, createElementAccessExpression(value, createNumericLiteral(i)), e)
              }
              else if (i == elements.length - 1) {
                emitDestructuringAssignment((<SpreadElementExpression>e).expression, createSliceCall(value, i), e)
              }
            }
          }
        }

        def emitDestructuringAssignment(target: Expression | ShorthandPropertyAssignment, value: Expression, sourceMapNode: Node) = {
          // When emitting target = value use source map node to highlight, including any temporary assignments needed for this
          if (target.kind == SyntaxKind.ShorthandPropertyAssignment) {
            if ((<ShorthandPropertyAssignment>target).objectAssignmentInitializer) {
              value = createDefaultValueCheck(value, (<ShorthandPropertyAssignment>target).objectAssignmentInitializer, sourceMapNode)
            }
            target = (<ShorthandPropertyAssignment>target).name
          }
          else if (target.kind == SyntaxKind.BinaryExpression && (<BinaryExpression>target).operatorToken.kind == SyntaxKind.EqualsToken) {
            value = createDefaultValueCheck(value, (<BinaryExpression>target).right, sourceMapNode)
            target = (<BinaryExpression>target).left
          }
          if (target.kind == SyntaxKind.ObjectLiteralExpression) {
            emitObjectLiteralAssignment(<ObjectLiteralExpression>target, value, sourceMapNode)
          }
          else if (target.kind == SyntaxKind.ArrayLiteralExpression) {
            emitArrayLiteralAssignment(<ArrayLiteralExpression>target, value, sourceMapNode)
          }
          else {
            emitAssignment(<Identifier>target, value, /*shouldEmitCommaBeforeAssignment*/ emitCount > 0, sourceMapNode)
            emitCount++
          }
        }

        def emitAssignmentExpression(root: BinaryExpression) = {
          val target = root.left
          var value = root.right

          if (isEmptyObjectLiteralOrArrayLiteral(target)) {
            emit(value)
          }
          else if (isAssignmentExpressionStatement) {
            // Source map node for root.left = root.right is root
            // but if root is synthetic, which could be in below case, use the target which is { a }
            // for ({a} of {a: String}) {
            // }
            emitDestructuringAssignment(target, value, nodeIsSynthesized(root) ? target : root)
          }
          else {
            if (root.parent.kind != SyntaxKind.ParenthesizedExpression) {
              write("(")
            }
            // Temporary assignment needed to emit root should highlight whole binary expression
            value = ensureIdentifier(value, /*reuseIdentifierExpressions*/ true, root)
            // Source map node for root.left = root.right is root
            emitDestructuringAssignment(target, value, root)
            write(", ")
            emit(value)
            if (root.parent.kind != SyntaxKind.ParenthesizedExpression) {
              write(")")
            }
          }
        }

        def emitBindingElement(target: BindingElement | VariableDeclaration, value: Expression) = {
          // Any temporary assignments needed to emit target = value should point to target
          if (target.initializer) {
            // Combine value and initializer
            value = value ? createDefaultValueCheck(value, target.initializer, target) : target.initializer
          }
          else if (!value) {
            // Use 'Unit 0' in absence of value and initializer
            value = createVoidZero()
          }
          if (isBindingPattern(target.name)) {
            val pattern = <BindingPattern>target.name
            val elements = pattern.elements
            val numElements = elements.length

            if (numElements != 1) {
              // For anything other than a single-element destructuring we need to generate a temporary
              // to ensure value is evaluated exactly once. Additionally, if we have zero elements
              // we need to emit *something* to ensure that in case a 'var' keyword was already emitted,
              // so in that case, we'll intentionally create that temporary.
              value = ensureIdentifier(value, /*reuseIdentifierExpressions*/ numElements != 0, target)
            }

            for (var i = 0; i < numElements; i++) {
              val element = elements[i]
              if (pattern.kind == SyntaxKind.ObjectBindingPattern) {
                // Rewrite element to a declaration with an initializer that fetches property
                val propName = element.propertyName || <Identifier>element.name
                emitBindingElement(element, createPropertyAccessForDestructuringProperty(value, propName))
              }
              else if (element.kind != SyntaxKind.OmittedExpression) {
                if (!element.dotDotDotToken) {
                  // Rewrite element to a declaration that accesses array element at index i
                  emitBindingElement(element, createElementAccessExpression(value, createNumericLiteral(i)))
                }
                else if (i == numElements - 1) {
                  emitBindingElement(element, createSliceCall(value, i))
                }
              }
            }
          }
          else {
            emitAssignment(<Identifier>target.name, value, /*shouldEmitCommaBeforeAssignment*/ emitCount > 0, target)
            emitCount++
          }
        }
      }

      def emitVariableDeclaration(node: VariableDeclaration) = {
        if (isBindingPattern(node.name)) {
          if (languageVersion < ScriptTarget.ES6) {
            emitDestructuring(node, /*isAssignmentExpressionStatement*/ false)
          }
          else {
            emit(node.name)
            emitOptional(" = ", node.initializer)
          }
        }
        else {
          var initializer = node.initializer
          if (!initializer &&
            languageVersion < ScriptTarget.ES6 &&
            // for names - binding patterns that lack initializer there is no point to emit explicit initializer
            // since downlevel codegen for destructuring will fail in the absence of initializer so all binding elements will say uninitialized
            node.name.kind == SyntaxKind.Identifier) {

            val container = getEnclosingBlockScopeContainer(node)
            val flags = resolver.getNodeCheckFlags(node)

            // nested var bindings might need to be initialized explicitly to preserve ES6 semantic
            // { var x = 1; }
            // { var x; } // x here should be (). not 1
            // NOTES:
            // Top level bindings never collide with anything and thus don't require explicit initialization.
            // As for nested var bindings there are two cases:
            // - nested var bindings that were not renamed definitely should be initialized explicitly
            //   { var x = 1; }
            //   { var x; if (some-condition) { x = 1}; if (x) { /*1*/ } }
            //   Without explicit initialization code in /*1*/ can be executed even if some-condition is evaluated to false
            // - renaming introduces fresh name that should not collide with any existing names, however renamed bindings sometimes also should be
            //   explicitly initialized. One particular case: non-captured binding declared inside loop body (but not in loop initializer)
            //   var x
            //   for (;;) {
            //     var x
            //   }
            //   in downlevel codegen inner 'x' will be renamed so it won't collide with outer 'x' however it will should be reset on every iteration
            //   as if it was declared anew.
            //   * Why non-captured binding - because if loop contains block scoped binding captured in some def then loop body will be rewritten
            //   to have a fresh scope on every iteration so everything will just work.
            //   * Why loop initializer is excluded - since we've introduced a fresh name it already will be ().
            val isCapturedInFunction = flags & NodeCheckFlags.CapturedBlockScopedBinding
            val isDeclaredInLoop = flags & NodeCheckFlags.BlockScopedBindingInLoop

            val emittedAsTopLevel =
              isBlockScopedContainerTopLevel(container) ||
              (isCapturedInFunction && isDeclaredInLoop && container.kind == SyntaxKind.Block && isIterationStatement(container.parent, /*lookInLabeledStatements*/ false))

            val emittedAsNestedLetDeclaration =
              getCombinedNodeFlags(node) & NodeFlags.Let &&
              !emittedAsTopLevel

            val emitExplicitInitializer =
              emittedAsNestedLetDeclaration &&
              container.kind != SyntaxKind.ForInStatement &&
              container.kind != SyntaxKind.ForOfStatement &&
              (
                !resolver.isDeclarationWithCollidingName(node) ||
                (isDeclaredInLoop && !isCapturedInFunction && !isIterationStatement(container, /*lookInLabeledStatements*/ false))
              )
            if (emitExplicitInitializer) {
              initializer = createVoidZero()
            }
          }

          val exportChanged = isNameOfExportedSourceLevelDeclarationInSystemExternalModule(node.name)

          if (exportChanged) {
            write(`${exportFunctionForFile}("`)
            emitNodeWithCommentsAndWithoutSourcemap(node.name)
            write(`", `)
          }

          emitModuleMemberName(node)
          emitOptional(" = ", initializer)

          if (exportChanged) {
            write(")")
          }
        }
      }

      def emitExportVariableAssignments(node: VariableDeclaration | BindingElement) = {
        if (node.kind == SyntaxKind.OmittedExpression) {
          return
        }
        val name = node.name
        if (name.kind == SyntaxKind.Identifier) {
          emitExportMemberAssignments(<Identifier>name)
        }
        else if (isBindingPattern(name)) {
          forEach((<BindingPattern>name).elements, emitExportVariableAssignments)
        }
      }

      def isES6ExportedDeclaration(node: Node) = {
        return !!(node.flags & NodeFlags.Export) &&
          modulekind == ModuleKind.ES6 &&
          node.parent.kind == SyntaxKind.SourceFile
      }

      def emitVariableStatement(node: VariableStatement) = {
        var startIsEmitted = false

        if (node.flags & NodeFlags.Export) {
          if (isES6ExportedDeclaration(node)) {
            // Exported ES6 module member
            write("export ")
            startIsEmitted = tryEmitStartOfVariableDeclarationList(node.declarationList)
          }
        }
        else {
          startIsEmitted = tryEmitStartOfVariableDeclarationList(node.declarationList)
        }

        if (startIsEmitted) {
          emitCommaList(node.declarationList.declarations)
          write(";")
        }
        else {
          val atLeastOneItem = emitVariableDeclarationListSkippingUninitializedEntries(node.declarationList)
          if (atLeastOneItem) {
            write(";")
          }
        }
        if (modulekind != ModuleKind.ES6 && node.parent == currentSourceFile) {
          forEach(node.declarationList.declarations, emitExportVariableAssignments)
        }
      }

      def shouldEmitLeadingAndTrailingCommentsForVariableStatement(node: VariableStatement) = {
        // If we're not exporting the variables, there's nothing special here.
        // Always emit comments for these nodes.
        if (!(node.flags & NodeFlags.Export)) {
          return true
        }

        // If we are exporting, but it's a top-level ES6 module exports,
        // we'll emit the declaration list verbatim, so emit comments too.
        if (isES6ExportedDeclaration(node)) {
          return true
        }

        // Otherwise, only emit if we have at least one initializer present.
        for (val declaration of node.declarationList.declarations) {
          if (declaration.initializer) {
            return true
          }
        }
        return false
      }

      def emitParameter(node: ParameterDeclaration) = {
        if (languageVersion < ScriptTarget.ES6) {
          if (isBindingPattern(node.name)) {
            val name = createTempVariable(TempFlags.Auto)
            if (!tempParameters) {
              tempParameters = []
            }
            tempParameters.push(name)
            emit(name)
          }
          else {
            emit(node.name)
          }
        }
        else {
          if (node.dotDotDotToken) {
            write("...")
          }
          emit(node.name)
          emitOptional(" = ", node.initializer)
        }
      }

      def emitDefaultValueAssignments(node: FunctionLikeDeclaration) = {
        if (languageVersion < ScriptTarget.ES6) {
          var tempIndex = 0
          forEach(node.parameters, parameter => {
            // A rest parameter cannot have a binding pattern or an initializer,
            // so var's just ignore it.
            if (parameter.dotDotDotToken) {
              return
            }

            val { name: paramName, initializer } = parameter
            if (isBindingPattern(paramName)) {
              // In cases where a binding pattern is simply '[]' or '{}',
              // we usually don't want to emit a var declaration; however, in the presence
              // of an initializer, we must emit that expression to preserve side effects.
              val hasBindingElements = paramName.elements.length > 0
              if (hasBindingElements || initializer) {
                writeLine()
                write("var ")

                if (hasBindingElements) {
                  emitDestructuring(parameter, /*isAssignmentExpressionStatement*/ false, tempParameters[tempIndex])
                }
                else {
                  emit(tempParameters[tempIndex])
                  write(" = ")
                  emit(initializer)
                }

                write(";")
                tempIndex++
              }
            }
            else if (initializer) {
              writeLine()
              emitStart(parameter)
              write("if (")
              emitNodeWithoutSourceMap(paramName)
              write(" == Unit 0)")
              emitEnd(parameter)
              write(" { ")
              emitStart(parameter)
              emitNodeWithCommentsAndWithoutSourcemap(paramName)
              write(" = ")
              emitNodeWithCommentsAndWithoutSourcemap(initializer)
              emitEnd(parameter)
              write("; }")
            }
          })
        }
      }

      def emitRestParameter(node: FunctionLikeDeclaration) = {
        if (languageVersion < ScriptTarget.ES6 && hasRestParameter(node)) {
          val restIndex = node.parameters.length - 1
          val restParam = node.parameters[restIndex]

          // A rest parameter cannot have a binding pattern, so var's just ignore it if it does.
          if (isBindingPattern(restParam.name)) {
            return
          }

          val tempName = createTempVariable(TempFlags._i).text
          writeLine()
          emitLeadingComments(restParam)
          emitStart(restParam)
          write("var ")
          emitNodeWithCommentsAndWithoutSourcemap(restParam.name)
          write(" = [];")
          emitEnd(restParam)
          emitTrailingComments(restParam)
          writeLine()
          write("for (")
          emitStart(restParam)
          write("var " + tempName + " = " + restIndex + ";")
          emitEnd(restParam)
          write(" ")
          emitStart(restParam)
          write(tempName + " < arguments.length;")
          emitEnd(restParam)
          write(" ")
          emitStart(restParam)
          write(tempName + "++")
          emitEnd(restParam)
          write(") {")
          increaseIndent()
          writeLine()
          emitStart(restParam)
          emitNodeWithCommentsAndWithoutSourcemap(restParam.name)
          write("[" + tempName + " - " + restIndex + "] = arguments[" + tempName + "];")
          emitEnd(restParam)
          decreaseIndent()
          writeLine()
          write("}")
        }
      }

      def emitAccessor(node: AccessorDeclaration) = {
        write(node.kind == SyntaxKind.GetAccessor ? "get " : "set ")
        emit(node.name)
        emitSignatureAndBody(node)
      }

      def shouldEmitAsArrowFunction(node: FunctionLikeDeclaration): Boolean = {
        return node.kind == SyntaxKind.ArrowFunction && languageVersion >= ScriptTarget.ES6
      }

      def emitDeclarationName(node: Declaration) = {
        if (node.name) {
          emitNodeWithCommentsAndWithoutSourcemap(node.name)
        }
        else {
          write(getGeneratedNameForNode(node))
        }
      }

      def shouldEmitFunctionName(node: FunctionLikeDeclaration) = {
        if (node.kind == SyntaxKind.FunctionExpression) {
          // Emit name if one is present
          return !!node.name
        }
        if (node.kind == SyntaxKind.FunctionDeclaration) {
          // Emit name if one is present, or emit generated name in down-level case (for default case)
          return !!node.name || modulekind != ModuleKind.ES6
        }
      }

      def emitFunctionDeclaration(node: FunctionLikeDeclaration) = {
        if (nodeIsMissing(node.body)) {
          return emitCommentsOnNotEmittedNode(node)
        }

        // TODO (yuisu) : we should not have special cases to condition emitting comments
        // but have one place to fix check for these conditions.
        val { kind, parent } = node
        if (kind != SyntaxKind.MethodDeclaration &&
          kind != SyntaxKind.MethodSignature &&
          parent &&
          parent.kind != SyntaxKind.PropertyAssignment &&
          parent.kind != SyntaxKind.CallExpression &&
          parent.kind != SyntaxKind.ArrayLiteralExpression) {
          // 1. Methods will emit comments at their assignment declaration sites.
          //
          // 2. If the def is a property of object literal, emitting leading-comments
          //  is done by emitNodeWithoutSourceMap which then call this def.
          //  In particular, we would like to avoid emit comments twice in following case:
          //
          //      var obj = {
          //        id:
          //          /*comment*/ () => Unit
          //      }
          //
          // 3. If the def is an argument in call expression, emitting of comments will be
          //  taken care of in emit list of arguments inside of 'emitCallExpression'.
          //
          // 4. If the def is in an array literal, 'emitLinePreservingList' will take care
          //  of leading comments.
          emitLeadingComments(node)
        }

        emitStart(node)
        // For targeting below es6, emit functions-like declaration including arrow def using def keyword.
        // When targeting ES6, emit arrow def natively in ES6 by omitting def keyword and using fat arrow instead
        if (!shouldEmitAsArrowFunction(node)) {
          if (isES6ExportedDeclaration(node)) {
            write("export ")
            if (node.flags & NodeFlags.Default) {
              write("default ")
            }
          }

          write("def")
          if (languageVersion >= ScriptTarget.ES6 && node.asteriskToken) {
            write("*")
          }
          write(" ")
        }

        if (shouldEmitFunctionName(node)) {
          emitDeclarationName(node)
        }

        emitSignatureAndBody(node)
        if (modulekind != ModuleKind.ES6 && kind == SyntaxKind.FunctionDeclaration && parent == currentSourceFile && node.name) {
          emitExportMemberAssignments((<FunctionDeclaration>node).name)
        }

        emitEnd(node)
        if (kind != SyntaxKind.MethodDeclaration && kind != SyntaxKind.MethodSignature) {
          emitTrailingComments(node)
        }
      }

      def emitCaptureThisForNodeIfNecessary(node: Node): Unit = {
        if (resolver.getNodeCheckFlags(node) & NodeCheckFlags.CaptureThis) {
          writeLine()
          emitStart(node)
          write("var _this = this;")
          emitEnd(node)
        }
      }

      def emitSignatureParameters(node: FunctionLikeDeclaration) = {
        increaseIndent()
        write("(")
        if (node) {
          val parameters = node.parameters
          val omitCount = languageVersion < ScriptTarget.ES6 && hasRestParameter(node) ? 1 : 0
          emitList(parameters, 0, parameters.length - omitCount, /*multiLine*/ false, /*trailingComma*/ false)
        }
        write(")")
        decreaseIndent()
      }

      def emitSignatureParametersForArrow(node: FunctionLikeDeclaration) = {
        // Check whether the parameter list needs parentheses and preserve no-parenthesis
        if (node.parameters.length == 1 && node.pos == node.parameters[0].pos) {
          emit(node.parameters[0])
          return
        }

        emitSignatureParameters(node)
      }

      def emitAsyncFunctionBodyForES6(node: FunctionLikeDeclaration) = {
        val promiseConstructor = getEntityNameFromTypeNode(node.type)
        val isArrowFunction = node.kind == SyntaxKind.ArrowFunction
        val hasLexicalArguments = (resolver.getNodeCheckFlags(node) & NodeCheckFlags.CaptureArguments) != 0

        // An async def is emit as an outer def that calls an inner
        // generator def. To preserve lexical bindings, we pass the current
        // `this` and `arguments` objects to `__awaiter`. The generator def
        // passed to `__awaiter` is executed inside of the callback to the
        // promise constructor.
        //
        // The emit for an async arrow without a lexical `arguments` binding might be:
        //
        //  // input
        //  var a = async (b) => { await b; }
        //
        //  // output
        //  var a = (b) => __awaiter(this, Unit 0, Unit 0, def* () {
        //    yield b
        //  })
        //
        // The emit for an async arrow with a lexical `arguments` binding might be:
        //
        //  // input
        //  var a = async (b) => { await arguments[0]; }
        //
        //  // output
        //  var a = (b) => __awaiter(this, arguments, Unit 0, def* (arguments) {
        //    yield arguments[0]
        //  })
        //
        // The emit for an async def expression without a lexical `arguments` binding
        // might be:
        //
        //  // input
        //  var a = async def (b) {
        //    await b
        //  }
        //
        //  // output
        //  var a = def (b) {
        //    return __awaiter(this, Unit 0, Unit 0, def* () {
        //      yield b
        //    })
        //  }
        //
        // The emit for an async def expression with a lexical `arguments` binding
        // might be:
        //
        //  // input
        //  var a = async def (b) {
        //    await arguments[0]
        //  }
        //
        //  // output
        //  var a = def (b) {
        //    return __awaiter(this, arguments, Unit 0, def* (_arguments) {
        //      yield _arguments[0]
        //    })
        //  }
        //
        // The emit for an async def expression with a lexical `arguments` binding
        // and a return type annotation might be:
        //
        //  // input
        //  var a = async def (b): MyPromise<any> {
        //    await arguments[0]
        //  }
        //
        //  // output
        //  var a = def (b) {
        //    return __awaiter(this, arguments, MyPromise, def* (_arguments) {
        //      yield _arguments[0]
        //    })
        //  }
        //

        // If this is not an async arrow, emit the opening brace of the def body
        // and the start of the return statement.
        if (!isArrowFunction) {
          write(" {")
          increaseIndent()
          writeLine()

          if (resolver.getNodeCheckFlags(node) & NodeCheckFlags.AsyncMethodWithSuperBinding) {
            writeLines(`
val _super = (def (geti, seti) {
  val cache = Object.create(null)
  return name => cache[name] || (cache[name] = { get value() { return geti(name); }, set value(v) { seti(name, v); } })
})(name => super[name], (name, value) => super[name] = value);`)
            writeLine()
          }
          else if (resolver.getNodeCheckFlags(node) & NodeCheckFlags.AsyncMethodWithSuper) {
            write(`val _super = name => super[name];`)
            writeLine()
          }

          write("return")
        }

        write(" __awaiter(this")
        if (hasLexicalArguments) {
          write(", arguments, ")
        }
        else {
          write(", Unit 0, ")
        }

        if (languageVersion >= ScriptTarget.ES6 || !promiseConstructor) {
          write("Unit 0")
        }
        else {
          emitEntityNameAsExpression(promiseConstructor, /*useFallback*/ false)
        }

        // Emit the call to __awaiter.
        write(", def* ()")

        // Emit the signature and body for the inner generator def.
        emitFunctionBody(node)
        write(")")

        // If this is not an async arrow, emit the closing brace of the outer def body.
        if (!isArrowFunction) {
          write(";")
          decreaseIndent()
          writeLine()
          write("}")
        }
      }

      def emitFunctionBody(node: FunctionLikeDeclaration) = {
        if (!node.body) {
          // There can be no body when there are parse errors.  Just emit an empty block
          // in that case.
          write(" { }")
        }
        else {
          if (node.body.kind == SyntaxKind.Block) {
            emitBlockFunctionBody(node, <Block>node.body)
          }
          else {
            emitExpressionFunctionBody(node, <Expression>node.body)
          }
        }
      }

      def emitSignatureAndBody(node: FunctionLikeDeclaration) = {
        val saveConvertedLoopState = convertedLoopState
        val saveTempFlags = tempFlags
        val saveTempVariables = tempVariables
        val saveTempParameters = tempParameters

        convertedLoopState = ()
        tempFlags = 0
        tempVariables = ()
        tempParameters = ()

        // When targeting ES6, emit arrow def natively in ES6
        if (shouldEmitAsArrowFunction(node)) {
          emitSignatureParametersForArrow(node)
          write(" =>")
        }
        else {
          emitSignatureParameters(node)
        }

        val isAsync = isAsyncFunctionLike(node)
        if (isAsync) {
          emitAsyncFunctionBodyForES6(node)
        }
        else {
          emitFunctionBody(node)
        }

        if (!isES6ExportedDeclaration(node)) {
          emitExportMemberAssignment(node)
        }

        Debug.assert(convertedLoopState == ())
        convertedLoopState = saveConvertedLoopState

        tempFlags = saveTempFlags
        tempVariables = saveTempVariables
        tempParameters = saveTempParameters
      }

      // Returns true if any preamble code was emitted.
      def emitFunctionBodyPreamble(node: FunctionLikeDeclaration): Unit = {
        emitCaptureThisForNodeIfNecessary(node)
        emitDefaultValueAssignments(node)
        emitRestParameter(node)
      }

      def emitExpressionFunctionBody(node: FunctionLikeDeclaration, body: Expression) = {
        if (languageVersion < ScriptTarget.ES6 || node.flags & NodeFlags.Async) {
          emitDownLevelExpressionFunctionBody(node, body)
          return
        }

        // For es6 and higher we can emit the expression as is.  However, in the case
        // where the expression might end up looking like a block when emitted, we'll
        // also wrap it in parentheses first.  For example if you have: a => <foo>{}
        // then we need to generate: a => ({})
        write(" ")

        // Unwrap all type assertions.
        var current = body
        while (current.kind == SyntaxKind.TypeAssertionExpression) {
          current = (<TypeAssertion>current).expression
        }

        emitParenthesizedIf(body, current.kind == SyntaxKind.ObjectLiteralExpression)
      }

      def emitDownLevelExpressionFunctionBody(node: FunctionLikeDeclaration, body: Expression) = {
        write(" {")
        increaseIndent()
        val outPos = writer.getTextPos()
        emitDetachedCommentsAndUpdateCommentsInfo(node.body)
        emitFunctionBodyPreamble(node)
        val preambleEmitted = writer.getTextPos() != outPos
        decreaseIndent()

        // If we didn't have to emit any preamble code, then attempt to keep the arrow
        // def on one line.
        if (!preambleEmitted && nodeStartPositionsAreOnSameLine(node, body)) {
          write(" ")
          emitStart(body)
          write("return ")
          emit(body)
          emitEnd(body)
          write(";")
          emitTempDeclarations(/*newLine*/ false)
          write(" ")
        }
        else {
          increaseIndent()
          writeLine()
          emitLeadingComments(node.body)
          emitStart(body)
          write("return ")
          emit(body)
          emitEnd(body)
          write(";")
          emitTrailingComments(node.body)

          emitTempDeclarations(/*newLine*/ true)
          decreaseIndent()
          writeLine()
        }

        emitStart(node.body)
        write("}")
        emitEnd(node.body)
      }

      def emitBlockFunctionBody(node: FunctionLikeDeclaration, body: Block) = {
        write(" {")
        val initialTextPos = writer.getTextPos()

        increaseIndent()
        emitDetachedCommentsAndUpdateCommentsInfo(body.statements)

        // Emit all the directive prologues (like "use strict").  These have to come before
        // any other preamble code we write (like parameter initializers).
        val startIndex = emitDirectivePrologues(body.statements, /*startWithNewLine*/ true)
        emitFunctionBodyPreamble(node)
        decreaseIndent()

        val preambleEmitted = writer.getTextPos() != initialTextPos

        if (!preambleEmitted && nodeEndIsOnSameLineAsNodeStart(body, body)) {
          for (val statement of body.statements) {
            write(" ")
            emit(statement)
          }
          emitTempDeclarations(/*newLine*/ false)
          write(" ")
          emitLeadingCommentsOfPosition(body.statements.end)
        }
        else {
          increaseIndent()
          emitLinesStartingAt(body.statements, startIndex)
          emitTempDeclarations(/*newLine*/ true)

          writeLine()
          emitLeadingCommentsOfPosition(body.statements.end)
          decreaseIndent()
        }

        emitToken(SyntaxKind.CloseBraceToken, body.statements.end)
      }

      /**
       * Return the statement at a given index if it is a super-call statement
       * @param ctor a constructor declaration
       * @param index an index to constructor's body to check
       */
      def getSuperCallAtGivenIndex(ctor: ConstructorDeclaration, index: Int): ExpressionStatement = {
        if (!ctor.body) {
          return ()
        }
        val statements = ctor.body.statements

        if (!statements || index >= statements.length) {
          return ()
        }

        val statement = statements[index]
        if (statement.kind == SyntaxKind.ExpressionStatement) {
          return isSuperCallExpression((<ExpressionStatement>statement).expression) ? <ExpressionStatement>statement : ()
        }
      }

      def emitParameterPropertyAssignments(node: ConstructorDeclaration) = {
        forEach(node.parameters, param => {
          if (param.flags & NodeFlags.AccessibilityModifier) {
            writeLine()
            emitStart(param)
            emitStart(param.name)
            write("this.")
            emitNodeWithoutSourceMap(param.name)
            emitEnd(param.name)
            write(" = ")
            emit(param.name)
            write(";")
            emitEnd(param)
          }
        })
      }

      def emitMemberAccessForPropertyName(memberName: DeclarationName) = {
        // This does not emit source map because it is emitted by caller as caller
        // is aware how the property name changes to the property access
        // eg. public x = 10; becomes this.x and static x = 10 becomes className.x
        if (memberName.kind == SyntaxKind.StringLiteral || memberName.kind == SyntaxKind.NumericLiteral) {
          write("[")
          emitNodeWithCommentsAndWithoutSourcemap(memberName)
          write("]")
        }
        else if (memberName.kind == SyntaxKind.ComputedPropertyName) {
          emitComputedPropertyName(<ComputedPropertyName>memberName)
        }
        else {
          write(".")
          emitNodeWithCommentsAndWithoutSourcemap(memberName)
        }
      }

      def getInitializedProperties(node: ClassLikeDeclaration, isStatic: Boolean) = {
        val properties: PropertyDeclaration[] = []
        for (val member of node.members) {
          if (member.kind == SyntaxKind.PropertyDeclaration && isStatic == ((member.flags & NodeFlags.Static) != 0) && (<PropertyDeclaration>member).initializer) {
            properties.push(<PropertyDeclaration>member)
          }
        }

        return properties
      }

      def emitPropertyDeclarations(node: ClassLikeDeclaration, properties: PropertyDeclaration[]) = {
        for (val property of properties) {
          emitPropertyDeclaration(node, property)
        }
      }

      def emitPropertyDeclaration(node: ClassLikeDeclaration, property: PropertyDeclaration, receiver?: Identifier, isExpression?: Boolean) = {
        writeLine()
        emitLeadingComments(property)
        emitStart(property)
        emitStart(property.name)
        if (receiver) {
          emit(receiver)
        }
        else {
          if (property.flags & NodeFlags.Static) {
            emitDeclarationName(node)
          }
          else {
            write("this")
          }
        }
        emitMemberAccessForPropertyName(property.name)
        emitEnd(property.name)
        write(" = ")
        emit(property.initializer)
        if (!isExpression) {
          write(";")
        }

        emitEnd(property)
        emitTrailingComments(property)
      }

      def emitMemberFunctionsForES5AndLower(node: ClassLikeDeclaration) = {
        forEach(node.members, member => {
          if (member.kind == SyntaxKind.SemicolonClassElement) {
            writeLine()
            write(";")
          }
          else if (member.kind == SyntaxKind.MethodDeclaration || node.kind == SyntaxKind.MethodSignature) {
            if (!(<MethodDeclaration>member).body) {
              return emitCommentsOnNotEmittedNode(member)
            }

            writeLine()
            emitLeadingComments(member)
            emitStart(member)
            emitStart((<MethodDeclaration>member).name)
            emitClassMemberPrefix(node, member)
            emitMemberAccessForPropertyName((<MethodDeclaration>member).name)
            emitEnd((<MethodDeclaration>member).name)
            write(" = ")
            emitFunctionDeclaration(<MethodDeclaration>member)
            emitEnd(member)
            write(";")
            emitTrailingComments(member)
          }
          else if (member.kind == SyntaxKind.GetAccessor || member.kind == SyntaxKind.SetAccessor) {
            val accessors = getAllAccessorDeclarations(node.members, <AccessorDeclaration>member)
            if (member == accessors.firstAccessor) {
              writeLine()
              emitStart(member)
              write("Object.defineProperty(")
              emitStart((<AccessorDeclaration>member).name)
              emitClassMemberPrefix(node, member)
              write(", ")
              emitExpressionForPropertyName((<AccessorDeclaration>member).name)
              emitEnd((<AccessorDeclaration>member).name)
              write(", {")
              increaseIndent()
              if (accessors.getAccessor) {
                writeLine()
                emitLeadingComments(accessors.getAccessor)
                write("get: ")
                emitStart(accessors.getAccessor)
                write("def ")
                emitSignatureAndBody(accessors.getAccessor)
                emitEnd(accessors.getAccessor)
                emitTrailingComments(accessors.getAccessor)
                write(",")
              }
              if (accessors.setAccessor) {
                writeLine()
                emitLeadingComments(accessors.setAccessor)
                write("set: ")
                emitStart(accessors.setAccessor)
                write("def ")
                emitSignatureAndBody(accessors.setAccessor)
                emitEnd(accessors.setAccessor)
                emitTrailingComments(accessors.setAccessor)
                write(",")
              }
              writeLine()
              write("enumerable: true,")
              writeLine()
              write("configurable: true")
              decreaseIndent()
              writeLine()
              write("});")
              emitEnd(member)
            }
          }
        })
      }

      def emitMemberFunctionsForES6AndHigher(node: ClassLikeDeclaration) = {
        for (val member of node.members) {
          if ((member.kind == SyntaxKind.MethodDeclaration || node.kind == SyntaxKind.MethodSignature) && !(<MethodDeclaration>member).body) {
            emitCommentsOnNotEmittedNode(member)
          }
          else if (member.kind == SyntaxKind.MethodDeclaration ||
            member.kind == SyntaxKind.GetAccessor ||
            member.kind == SyntaxKind.SetAccessor) {
            writeLine()
            emitLeadingComments(member)
            emitStart(member)
            if (member.flags & NodeFlags.Static) {
              write("static ")
            }

            if (member.kind == SyntaxKind.GetAccessor) {
              write("get ")
            }
            else if (member.kind == SyntaxKind.SetAccessor) {
              write("set ")
            }
            if ((<MethodDeclaration>member).asteriskToken) {
              write("*")
            }
            emit((<MethodDeclaration>member).name)
            emitSignatureAndBody(<MethodDeclaration>member)
            emitEnd(member)
            emitTrailingComments(member)
          }
          else if (member.kind == SyntaxKind.SemicolonClassElement) {
            writeLine()
            write(";")
          }
        }
      }

      def emitConstructor(node: ClassLikeDeclaration, baseTypeElement: ExpressionWithTypeArguments) = {
        val saveConvertedLoopState = convertedLoopState
        val saveTempFlags = tempFlags
        val saveTempVariables = tempVariables
        val saveTempParameters = tempParameters

        convertedLoopState = ()
        tempFlags = 0
        tempVariables = ()
        tempParameters = ()

        emitConstructorWorker(node, baseTypeElement)

        Debug.assert(convertedLoopState == ())
        convertedLoopState = saveConvertedLoopState

        tempFlags = saveTempFlags
        tempVariables = saveTempVariables
        tempParameters = saveTempParameters
      }

      def emitConstructorWorker(node: ClassLikeDeclaration, baseTypeElement: ExpressionWithTypeArguments) = {
        // Check if we have property assignment inside class declaration.
        // If there is property assignment, we need to emit constructor whether users define it or not
        // If there is no property assignment, we can omit constructor if users do not define it
        var hasInstancePropertyWithInitializer = false

        // Emit the constructor overload pinned comments
        forEach(node.members, member => {
          if (member.kind == SyntaxKind.Constructor && !(<ConstructorDeclaration>member).body) {
            emitCommentsOnNotEmittedNode(member)
          }
          // Check if there is any non-static property assignment
          if (member.kind == SyntaxKind.PropertyDeclaration && (<PropertyDeclaration>member).initializer && (member.flags & NodeFlags.Static) == 0) {
            hasInstancePropertyWithInitializer = true
          }
        })

        val ctor = getFirstConstructorWithBody(node)

        // For target ES6 and above, if there is no user-defined constructor and there is no property assignment
        // do not emit constructor in class declaration.
        if (languageVersion >= ScriptTarget.ES6 && !ctor && !hasInstancePropertyWithInitializer) {
          return
        }

        if (ctor) {
          emitLeadingComments(ctor)
        }
        emitStart(ctor || node)

        if (languageVersion < ScriptTarget.ES6) {
          write("def ")
          emitDeclarationName(node)
          emitSignatureParameters(ctor)
        }
        else {
          write("constructor")
          if (ctor) {
            emitSignatureParameters(ctor)
          }
          else {
            // Based on EcmaScript6 section 14.5.14: Runtime Semantics: ClassDefinitionEvaluation.
            // If constructor is empty, then,
            //    If ClassHeritageopt is present, then
            //      Let constructor be the result of parsing the String "constructor(... args){ super (...args);}" using the syntactic grammar with the goal symbol MethodDefinition.
            //    Else,
            //      Let constructor be the result of parsing the String "constructor( ){ }" using the syntactic grammar with the goal symbol MethodDefinition
            if (baseTypeElement) {
              write("(...args)")
            }
            else {
              write("()")
            }
          }
        }

        var startIndex = 0

        write(" {")
        increaseIndent()
        if (ctor) {
          // Emit all the directive prologues (like "use strict").  These have to come before
          // any other preamble code we write (like parameter initializers).
          startIndex = emitDirectivePrologues(ctor.body.statements, /*startWithNewLine*/ true)
          emitDetachedCommentsAndUpdateCommentsInfo(ctor.body.statements)
        }
        emitCaptureThisForNodeIfNecessary(node)
        var superCall: ExpressionStatement
        if (ctor) {
          emitDefaultValueAssignments(ctor)
          emitRestParameter(ctor)

          if (baseTypeElement) {
            superCall = getSuperCallAtGivenIndex(ctor, startIndex)
            if (superCall) {
              writeLine()
              emit(superCall)
            }
          }

          emitParameterPropertyAssignments(ctor)
        }
        else {
          if (baseTypeElement) {
            writeLine()
            emitStart(baseTypeElement)
            if (languageVersion < ScriptTarget.ES6) {
              write("_super.apply(this, arguments);")
            }
            else {
              write("super(...args);")
            }
            emitEnd(baseTypeElement)
          }
        }
        emitPropertyDeclarations(node, getInitializedProperties(node, /*isStatic*/ false))
        if (ctor) {
          var statements: Node[] = (<Block>ctor.body).statements
          if (superCall) {
            statements = statements.slice(1)
          }
          emitLinesStartingAt(statements, startIndex)
        }
        emitTempDeclarations(/*newLine*/ true)
        writeLine()
        if (ctor) {
          emitLeadingCommentsOfPosition((<Block>ctor.body).statements.end)
        }
        decreaseIndent()
        emitToken(SyntaxKind.CloseBraceToken, ctor ? (<Block>ctor.body).statements.end : node.members.end)
        emitEnd(<Node>ctor || node)
        if (ctor) {
          emitTrailingComments(ctor)
        }
      }

      def emitClassExpression(node: ClassExpression) = {
        return emitClassLikeDeclaration(node)
      }

      def emitClassDeclaration(node: ClassDeclaration) = {
        return emitClassLikeDeclaration(node)
      }

      def emitClassLikeDeclaration(node: ClassLikeDeclaration) = {
        if (languageVersion < ScriptTarget.ES6) {
          emitClassLikeDeclarationBelowES6(node)
        }
        else {
          emitClassLikeDeclarationForES6AndHigher(node)
        }
        if (modulekind != ModuleKind.ES6 && node.parent == currentSourceFile && node.name) {
          emitExportMemberAssignments(node.name)
        }
      }

      def emitClassLikeDeclarationForES6AndHigher(node: ClassLikeDeclaration) = {
        var decoratedClassAlias: String
        val thisNodeIsDecorated = nodeIsDecorated(node)
        if (node.kind == SyntaxKind.ClassDeclaration) {
          if (thisNodeIsDecorated) {
            // When we emit an ES6 class that has a class decorator, we must tailor the
            // emit to certain specific cases.
            //
            // In the simplest case, we emit the class declaration as a var declaration, and
            // evaluate decorators after the close of the class body:
            //
            //  TypeScript            | Javascript
            //  --------------------------------|------------------------------------
            //  @dec              | var C = class C {
            //  class C {             | }
            //  }                 | C = __decorate([dec], C)
            //  --------------------------------|------------------------------------
            //  @dec              | var C = class C {
            //  class C {        | }
            //  }                 | C = __decorate([dec], C)
            //  ---------------------------------------------------------------------
            //  [Example 1]
            //
            // If a class declaration contains a reference to itself *inside* of the class body,
            // this introduces two bindings to the class: One outside of the class body, and one
            // inside of the class body. If we apply decorators as in [Example 1] above, there
            // is the possibility that the decorator `dec` will return a new value for the
            // constructor, which would result in the binding inside of the class no longer
            // pointing to the same reference as the binding outside of the class.
            //
            // As a result, we must instead rewrite all references to the class *inside* of the
            // class body to instead point to a local temporary alias for the class:
            //
            //  TypeScript            | Javascript
            //  --------------------------------|------------------------------------
            //  @dec              | var C_1
            //  class C {             | var C = C_1 = class C {
            //  static x() { return C.y; }  |   static x() { return C_1.y; }
            //  static y = 1;         | }
            //  }                 | C.y = 1
            //                  | C = C_1 = __decorate([dec], C)
            //  --------------------------------|------------------------------------
            //  @dec              | var C_1
            //  class C {        | var C = C_1 = class C {
            //  static x() { return C.y; }  |   static x() { return C_1.y; }
            //  static y = 1;         | }
            //  }                 | C.y = 1
            //                  | C = C_1 = __decorate([dec], C)
            //  ---------------------------------------------------------------------
            //  [Example 2]
            //
            // If a class declaration is the default of a module, we instead emit
            // the after the decorated declaration:
            //
            //  TypeScript            | Javascript
            //  --------------------------------|------------------------------------
            //  @dec              | var default_1 = class {
            //  default class {      | }
            //  }                 | default_1 = __decorate([dec], default_1)
            //                  | default default_1
            //  --------------------------------|------------------------------------
            //  @dec              | var C = class C {
            //  default class {      | }
            //  }                 | C = __decorate([dec], C)
            //                  | default C
            //  ---------------------------------------------------------------------
            //  [Example 3]
            //
            // If the class declaration is the default and a reference to itself
            // inside of the class body, we must emit both an alias for the class *and*
            // move the after the declaration:
            //
            //  TypeScript            | Javascript
            //  --------------------------------|------------------------------------
            //  @dec              | var C_1
            //  default class C {    | var C = C_1 = class C {
            //  static x() { return C.y; }  |   static x() { return C_1.y; }
            //  static y = 1;         | }
            //  }                 | C.y = 1
            //                  | C = C_1 = __decorate([dec], C)
            //                  | default C
            //  ---------------------------------------------------------------------
            //  [Example 4]
            //

            if (resolver.getNodeCheckFlags(node) & NodeCheckFlags.ClassWithBodyScopedClassBinding) {
              decoratedClassAlias = unescapeIdentifier(makeUniqueName(node.name ? node.name.text : "default"))
              decoratedClassAliases[getNodeId(node)] = decoratedClassAlias
              write(`var ${decoratedClassAlias};`)
              writeLine()
            }

            if (isES6ExportedDeclaration(node) && !(node.flags & NodeFlags.Default)) {
              write("export ")
            }

            write("var ")
            emitDeclarationName(node)
            if (decoratedClassAlias != ()) {
              write(` = ${decoratedClassAlias}`)
            }

            write(" = ")
          }
          else if (isES6ExportedDeclaration(node)) {
            write("export ")
            if (node.flags & NodeFlags.Default) {
              write("default ")
            }
          }
        }

        // If the class has static properties, and it's a class expression, then we'll need
        // to specialize the emit a bit.  for a class expression of the form:
        //
        //    class C { static a = 1; static b = 2; ... }
        //
        // We'll emit:
        //
        //    (_temp = class C { ... }, _temp.a = 1, _temp.b = 2, _temp)
        //
        // This keeps the expression as an expression, while ensuring that the static parts
        // of it have been initialized by the time it is used.
        val staticProperties = getInitializedProperties(node, /*isStatic*/ true)
        val isClassExpressionWithStaticProperties = staticProperties.length > 0 && node.kind == SyntaxKind.ClassExpression
        var tempVariable: Identifier

        if (isClassExpressionWithStaticProperties) {
          tempVariable = createAndRecordTempVariable(TempFlags.Auto)
          write("(")
          increaseIndent()
          emit(tempVariable)
          write(" = ")
        }

        write("class")

        // emit name if
        // - node has a name
        // - this is default with static initializers
        if (node.name || (node.flags & NodeFlags.Default && (staticProperties.length > 0 || modulekind != ModuleKind.ES6) && !thisNodeIsDecorated)) {
          write(" ")
          emitDeclarationName(node)
        }

        val baseTypeNode = getClassExtendsHeritageClauseElement(node)
        if (baseTypeNode) {
          write(" extends ")
          emit(baseTypeNode.expression)
        }

        write(" {")
        increaseIndent()
        writeLine()
        emitConstructor(node, baseTypeNode)
        emitMemberFunctionsForES6AndHigher(node)
        decreaseIndent()
        writeLine()
        emitToken(SyntaxKind.CloseBraceToken, node.members.end)

        if (thisNodeIsDecorated) {
          decoratedClassAliases[getNodeId(node)] = ()
          write(";")
        }

        // Emit static property assignment. Because classDeclaration is lexically evaluated,
        // it is safe to emit static property assignment after classDeclaration
        // From ES6 specification:
        //    HasLexicalDeclaration (N) : Determines if the argument identifier has a binding in this environment record that was created using
        //                  a lexical declaration such as a LexicalDeclaration or a ClassDeclaration.

        if (isClassExpressionWithStaticProperties) {
          for (val property of staticProperties) {
            write(",")
            writeLine()
            emitPropertyDeclaration(node, property, /*receiver*/ tempVariable, /*isExpression*/ true)
          }
          write(",")
          writeLine()
          emit(tempVariable)
          decreaseIndent()
          write(")")
        }
        else {
          writeLine()
          emitPropertyDeclarations(node, staticProperties)
          emitDecoratorsOfClass(node, decoratedClassAlias)
        }

        if (!(node.flags & NodeFlags.Export)) {
          return
        }
        if (modulekind != ModuleKind.ES6) {
          emitExportMemberAssignment(node as ClassDeclaration)
        }
        else {
          // If this is an exported class, but not on the top level (i.e. on an internal
          // module), it
          if (node.flags & NodeFlags.Default) {
            // if this is a top level default of decorated class, write the after the declaration.
            if (thisNodeIsDecorated) {
              writeLine()
              write("export default ")
              emitDeclarationName(node)
              write(";")
            }
          }
          else if (node.parent.kind != SyntaxKind.SourceFile) {
            writeLine()
            emitStart(node)
            emitModuleMemberName(node)
            write(" = ")
            emitDeclarationName(node)
            emitEnd(node)
            write(";")
          }
        }
      }

      def emitClassLikeDeclarationBelowES6(node: ClassLikeDeclaration) = {
        if (node.kind == SyntaxKind.ClassDeclaration) {
          // source file level classes in system modules are hoisted so 'var's for them are already defined
          if (!shouldHoistDeclarationInSystemJsModule(node)) {
            write("var ")
          }
          emitDeclarationName(node)
          write(" = ")
        }

        write("(def (")
        val baseTypeNode = getClassExtendsHeritageClauseElement(node)
        if (baseTypeNode) {
          write("_super")
        }
        write(") {")
        val saveTempFlags = tempFlags
        val saveTempVariables = tempVariables
        val saveTempParameters = tempParameters
        val saveComputedPropertyNamesToGeneratedNames = computedPropertyNamesToGeneratedNames
        val saveConvertedLoopState = convertedLoopState

        convertedLoopState = ()
        tempFlags = 0
        tempVariables = ()
        tempParameters = ()
        computedPropertyNamesToGeneratedNames = ()
        increaseIndent()
        if (baseTypeNode) {
          writeLine()
          emitStart(baseTypeNode)
          write("__extends(")
          emitDeclarationName(node)
          write(", _super);")
          emitEnd(baseTypeNode)
        }
        writeLine()
        emitConstructor(node, baseTypeNode)
        emitMemberFunctionsForES5AndLower(node)
        emitPropertyDeclarations(node, getInitializedProperties(node, /*isStatic*/ true))
        writeLine()
        emitDecoratorsOfClass(node, /*decoratedClassAlias*/ ())
        writeLine()
        emitToken(SyntaxKind.CloseBraceToken, node.members.end, () => {
          write("return ")
          emitDeclarationName(node)
        })
        write(";")
        emitTempDeclarations(/*newLine*/ true)

        Debug.assert(convertedLoopState == ())
        convertedLoopState = saveConvertedLoopState

        tempFlags = saveTempFlags
        tempVariables = saveTempVariables
        tempParameters = saveTempParameters
        computedPropertyNamesToGeneratedNames = saveComputedPropertyNamesToGeneratedNames
        decreaseIndent()
        writeLine()
        emitToken(SyntaxKind.CloseBraceToken, node.members.end)
        emitStart(node)
        write("(")
        if (baseTypeNode) {
          emit(baseTypeNode.expression)
        }
        write("))")
        if (node.kind == SyntaxKind.ClassDeclaration) {
          write(";")
        }
        emitEnd(node)

        if (node.kind == SyntaxKind.ClassDeclaration) {
          emitExportMemberAssignment(<ClassDeclaration>node)
        }
      }

      def emitClassMemberPrefix(node: ClassLikeDeclaration, member: Node) = {
        emitDeclarationName(node)
        if (!(member.flags & NodeFlags.Static)) {
          write(".prototype")
        }
      }

      def emitDecoratorsOfClass(node: ClassLikeDeclaration, decoratedClassAlias: String) = {
        emitDecoratorsOfMembers(node, /*staticFlag*/ 0)
        emitDecoratorsOfMembers(node, NodeFlags.Static)
        emitDecoratorsOfConstructor(node, decoratedClassAlias)
      }

      def emitDecoratorsOfConstructor(node: ClassLikeDeclaration, decoratedClassAlias: String) = {
        val decorators = node.decorators
        val constructor = getFirstConstructorWithBody(node)
        val firstParameterDecorator = constructor && forEach(constructor.parameters, parameter => parameter.decorators)

        // skip decoration of the constructor if neither it nor its parameters are decorated
        if (!decorators && !firstParameterDecorator) {
          return
        }

        // Emit the call to __decorate. Given the class:
        //
        //   @dec
        //   class C {
        //   }
        //
        // The emit for the class is:
        //
        //   C = __decorate([dec], C)
        //

        writeLine()
        emitStart(node.decorators || firstParameterDecorator)
        emitDeclarationName(node)
        if (decoratedClassAlias != ()) {
          write(` = ${decoratedClassAlias}`)
        }

        write(" = __decorate([")
        increaseIndent()
        writeLine()

        val decoratorCount = decorators ? decorators.length : 0
        var argumentsWritten = emitList(decorators, 0, decoratorCount, /*multiLine*/ true, /*trailingComma*/ false, /*leadingComma*/ false, /*noTrailingNewLine*/ true,
          decorator => emit(decorator.expression))
        if (firstParameterDecorator) {
          argumentsWritten += emitDecoratorsOfParameters(constructor, /*leadingComma*/ argumentsWritten > 0)
        }
        emitSerializedTypeMetadata(node, /*leadingComma*/ argumentsWritten >= 0)

        decreaseIndent()
        writeLine()
        write("], ")
        emitDeclarationName(node)
        write(")")
        emitEnd(node.decorators || firstParameterDecorator)
        write(";")
        writeLine()
      }

      def emitDecoratorsOfMembers(node: ClassLikeDeclaration, staticFlag: NodeFlags) = {
        for (val member of node.members) {
          // only emit members in the correct group
          if ((member.flags & NodeFlags.Static) != staticFlag) {
            continue
          }

          // skip members that cannot be decorated (such as the constructor)
          if (!nodeCanBeDecorated(member)) {
            continue
          }

          // skip an accessor declaration if it is not the first accessor
          var decorators: NodeArray<Decorator>
          var functionLikeMember: FunctionLikeDeclaration
          if (isAccessor(member)) {
            val accessors = getAllAccessorDeclarations(node.members, <AccessorDeclaration>member)
            if (member != accessors.firstAccessor) {
              continue
            }

            // get the decorators from the first accessor with decorators
            decorators = accessors.firstAccessor.decorators
            if (!decorators && accessors.secondAccessor) {
              decorators = accessors.secondAccessor.decorators
            }

            // we only decorate parameters of the set accessor
            functionLikeMember = accessors.setAccessor
          }
          else {
            decorators = member.decorators

            // we only decorate the parameters here if this is a method
            if (member.kind == SyntaxKind.MethodDeclaration) {
              functionLikeMember = <MethodDeclaration>member
            }
          }
          val firstParameterDecorator = functionLikeMember && forEach(functionLikeMember.parameters, parameter => parameter.decorators)

          // skip a member if it or any of its parameters are not decorated
          if (!decorators && !firstParameterDecorator) {
            continue
          }

          // Emit the call to __decorate. Given the following:
          //
          //   class C {
          //   @dec method(@dec2 x) {}
          //   @dec get accessor() {}
          //   @dec prop
          //   }
          //
          // The emit for a method is:
          //
          //   __decorate([
          //     dec,
          //     __param(0, dec2),
          //     __metadata("design:type", Function),
          //     __metadata("design:paramtypes", [Object]),
          //     __metadata("design:returntype", Unit 0)
          //   ], C.prototype, "method", ())
          //
          // The emit for an accessor is:
          //
          //   __decorate([
          //     dec
          //   ], C.prototype, "accessor", ())
          //
          // The emit for a property is:
          //
          //   __decorate([
          //     dec
          //   ], C.prototype, "prop")
          //

          writeLine()
          emitStart(decorators || firstParameterDecorator)
          write("__decorate([")
          increaseIndent()
          writeLine()

          val decoratorCount = decorators ? decorators.length : 0
          var argumentsWritten = emitList(decorators, 0, decoratorCount, /*multiLine*/ true, /*trailingComma*/ false, /*leadingComma*/ false, /*noTrailingNewLine*/ true,
            decorator => emit(decorator.expression))

          if (firstParameterDecorator) {
            argumentsWritten += emitDecoratorsOfParameters(functionLikeMember, argumentsWritten > 0)
          }
          emitSerializedTypeMetadata(member, argumentsWritten > 0)

          decreaseIndent()
          writeLine()
          write("], ")
          emitClassMemberPrefix(node, member)
          write(", ")
          emitExpressionForPropertyName(member.name)

          if (languageVersion > ScriptTarget.ES3) {
            if (member.kind != SyntaxKind.PropertyDeclaration) {
              // We emit `null` here to indicate to `__decorate` that it can invoke `Object.getOwnPropertyDescriptor` directly.
              // We have this extra argument here so that we can inject an explicit property descriptor at a later date.
              write(", null")
            }
            else {
              // We emit `Unit 0` here to indicate to `__decorate` that it can invoke `Object.defineProperty` directly, but that it
              // should not invoke `Object.getOwnPropertyDescriptor`.
              write(", Unit 0")
            }
          }

          write(")")
          emitEnd(decorators || firstParameterDecorator)
          write(";")
          writeLine()
        }
      }

      def emitDecoratorsOfParameters(node: FunctionLikeDeclaration, leadingComma: Boolean): Int = {
        var argumentsWritten = 0
        if (node) {
          var parameterIndex = 0
          for (val parameter of node.parameters) {
            if (nodeIsDecorated(parameter)) {
              val decorators = parameter.decorators
              argumentsWritten += emitList(decorators, 0, decorators.length, /*multiLine*/ true, /*trailingComma*/ false, /*leadingComma*/ leadingComma, /*noTrailingNewLine*/ true, decorator => {
                write(`__param(${parameterIndex}, `)
                emit(decorator.expression)
                write(")")
              })
              leadingComma = true
            }
            parameterIndex++
          }
        }
        return argumentsWritten
      }

      def shouldEmitTypeMetadata(node: Declaration): Boolean = {
        // This method determines whether to emit the "design:type" metadata based on the node's kind.
        // The caller should have already tested whether the node has decorators and whether the emitDecoratorMetadata
        // compiler option is set.
        switch (node.kind) {
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.GetAccessor:
          case SyntaxKind.SetAccessor:
          case SyntaxKind.PropertyDeclaration:
            return true
        }

        return false
      }

      def shouldEmitReturnTypeMetadata(node: Declaration): Boolean = {
        // This method determines whether to emit the "design:returntype" metadata based on the node's kind.
        // The caller should have already tested whether the node has decorators and whether the emitDecoratorMetadata
        // compiler option is set.
        switch (node.kind) {
          case SyntaxKind.MethodDeclaration:
            return true
        }
        return false
      }

      def shouldEmitParamTypesMetadata(node: Declaration): Boolean = {
        // This method determines whether to emit the "design:paramtypes" metadata based on the node's kind.
        // The caller should have already tested whether the node has decorators and whether the emitDecoratorMetadata
        // compiler option is set.
        switch (node.kind) {
          case SyntaxKind.ClassDeclaration:
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.SetAccessor:
            return true
        }
        return false
      }

      /** Serializes the type of a declaration to an appropriate JS constructor value. Used by the __metadata decorator for a class member. */
      def emitSerializedTypeOfNode(node: Node) = {
        // serialization of the type of a declaration uses the following rules:
        //
        // * The serialized type of a ClassDeclaration is "Function"
        // * The serialized type of a ParameterDeclaration is the serialized type of its type annotation.
        // * The serialized type of a PropertyDeclaration is the serialized type of its type annotation.
        // * The serialized type of an AccessorDeclaration is the serialized type of the return type annotation of its getter or parameter type annotation of its setter.
        // * The serialized type of any other FunctionLikeDeclaration is "Function".
        // * The serialized type of any other node is "Unit 0".
        //
        // For rules on serializing type annotations, see `serializeTypeNode`.
        switch (node.kind) {
          case SyntaxKind.ClassDeclaration:
            write("Function")
            return

          case SyntaxKind.PropertyDeclaration:
            emitSerializedTypeNode((<PropertyDeclaration>node).type)
            return

          case SyntaxKind.Parameter:
            emitSerializedTypeNode((<ParameterDeclaration>node).type)
            return

          case SyntaxKind.GetAccessor:
            emitSerializedTypeNode((<AccessorDeclaration>node).type)
            return

          case SyntaxKind.SetAccessor:
            emitSerializedTypeNode(getSetAccessorTypeAnnotationNode(<AccessorDeclaration>node))
            return

        }

        if (isFunctionLike(node)) {
          write("Function")
          return
        }

        write("Unit 0")
      }

      def emitSerializedTypeNode(node: TypeNode) = {
        if (node) {
          switch (node.kind) {
            case SyntaxKind.VoidKeyword:
              write("Unit 0")
              return

            case SyntaxKind.ParenthesizedType:
              emitSerializedTypeNode((<ParenthesizedTypeNode>node).type)
              return

            case SyntaxKind.FunctionType:
            case SyntaxKind.ConstructorType:
              write("Function")
              return

            case SyntaxKind.ArrayType:
            case SyntaxKind.TupleType:
              write("Array")
              return

            case SyntaxKind.TypePredicate:
            case SyntaxKind.BooleanKeyword:
              write("Boolean")
              return

            case SyntaxKind.StringKeyword:
            case SyntaxKind.StringLiteralType:
              write("String")
              return

            case SyntaxKind.NumberKeyword:
              write("Number")
              return

            case SyntaxKind.SymbolKeyword:
              write("Symbol")
              return

            case SyntaxKind.TypeReference:
              emitSerializedTypeReferenceNode(<TypeReferenceNode>node)
              return

            case SyntaxKind.TypeQuery:
            case SyntaxKind.TypeLiteral:
            case SyntaxKind.UnionType:
            case SyntaxKind.IntersectionType:
            case SyntaxKind.AnyKeyword:
            case SyntaxKind.ThisType:
              break

            default:
              Debug.fail("Cannot serialize unexpected type node.")
              break
          }
        }
        write("Object")
      }

      /** Serializes a TypeReferenceNode to an appropriate JS constructor value. Used by the __metadata decorator. */
      def emitSerializedTypeReferenceNode(node: TypeReferenceNode) = {
        var location: Node = node.parent
        while (isDeclaration(location) || isTypeNode(location)) {
          location = location.parent
        }

        // Clone the type name and parent it to a location outside of the current declaration.
        val typeName = cloneEntityName(node.typeName, location)
        val result = resolver.getTypeReferenceSerializationKind(typeName)
        switch (result) {
          case TypeReferenceSerializationKind.Unknown:
            var temp = createAndRecordTempVariable(TempFlags.Auto)
            write("(typeof (")
            emitNodeWithoutSourceMap(temp)
            write(" = ")
            emitEntityNameAsExpression(typeName, /*useFallback*/ true)
            write(") == 'def' && ")
            emitNodeWithoutSourceMap(temp)
            write(") || Object")
            break

          case TypeReferenceSerializationKind.TypeWithConstructSignatureAndValue:
            emitEntityNameAsExpression(typeName, /*useFallback*/ false)
            break

          case TypeReferenceSerializationKind.VoidType:
            write("Unit 0")
            break

          case TypeReferenceSerializationKind.BooleanType:
            write("Boolean")
            break

          case TypeReferenceSerializationKind.NumberLikeType:
            write("Number")
            break

          case TypeReferenceSerializationKind.StringLikeType:
            write("String")
            break

          case TypeReferenceSerializationKind.ArrayLikeType:
            write("Array")
            break

          case TypeReferenceSerializationKind.ESSymbolType:
            if (languageVersion < ScriptTarget.ES6) {
              write("typeof Symbol == 'def' ? Symbol : Object")
            }
            else {
              write("Symbol")
            }
            break

          case TypeReferenceSerializationKind.TypeWithCallSignature:
            write("Function")
            break

          case TypeReferenceSerializationKind.ObjectType:
            write("Object")
            break
        }
      }

      /** Serializes the parameter types of a def or the constructor of a class. Used by the __metadata decorator for a method or set accessor. */
      def emitSerializedParameterTypesOfNode(node: Node) = {
        // serialization of parameter types uses the following rules:
        //
        // * If the declaration is a class, the parameters of the first constructor with a body are used.
        // * If the declaration is def-like and has a body, the parameters of the def are used.
        //
        // For the rules on serializing the type of each parameter declaration, see `serializeTypeOfDeclaration`.
        if (node) {
          var valueDeclaration: FunctionLikeDeclaration
          if (node.kind == SyntaxKind.ClassDeclaration) {
            valueDeclaration = getFirstConstructorWithBody(<ClassDeclaration>node)
          }
          else if (isFunctionLike(node) && nodeIsPresent((<FunctionLikeDeclaration>node).body)) {
            valueDeclaration = <FunctionLikeDeclaration>node
          }

          if (valueDeclaration) {
            val parameters = valueDeclaration.parameters
            val parameterCount = parameters.length
            if (parameterCount > 0) {
              for (var i = 0; i < parameterCount; i++) {
                if (i > 0) {
                  write(", ")
                }

                if (parameters[i].dotDotDotToken) {
                  var parameterType = parameters[i].type
                  if (parameterType.kind == SyntaxKind.ArrayType) {
                    parameterType = (<ArrayTypeNode>parameterType).elementType
                  }
                  else if (parameterType.kind == SyntaxKind.TypeReference && (<TypeReferenceNode>parameterType).typeArguments && (<TypeReferenceNode>parameterType).typeArguments.length == 1) {
                    parameterType = (<TypeReferenceNode>parameterType).typeArguments[0]
                  }
                  else {
                    parameterType = ()
                  }

                  emitSerializedTypeNode(parameterType)
                }
                else {
                  emitSerializedTypeOfNode(parameters[i])
                }
              }
            }
          }
        }
      }

      /** Serializes the return type of def. Used by the __metadata decorator for a method. */
      def emitSerializedReturnTypeOfNode(node: Node) = {
        if (node && isFunctionLike(node) && (<FunctionLikeDeclaration>node).type) {
          emitSerializedTypeNode((<FunctionLikeDeclaration>node).type)
          return
        }

        write("Unit 0")
      }


      def emitSerializedTypeMetadata(node: Declaration, writeComma: Boolean): Int = {
        // This method emits the serialized type metadata for a decorator target.
        // The caller should have already tested whether the node has decorators.
        var argumentsWritten = 0
        if (compilerOptions.emitDecoratorMetadata) {
          if (shouldEmitTypeMetadata(node)) {
            if (writeComma) {
              write(", ")
            }
            writeLine()
            write("__metadata('design:type', ")
            emitSerializedTypeOfNode(node)
            write(")")
            argumentsWritten++
          }
          if (shouldEmitParamTypesMetadata(node)) {
            if (writeComma || argumentsWritten) {
              write(", ")
            }
            writeLine()
            write("__metadata('design:paramtypes', [")
            emitSerializedParameterTypesOfNode(node)
            write("])")
            argumentsWritten++
          }
          if (shouldEmitReturnTypeMetadata(node)) {
            if (writeComma || argumentsWritten) {
              write(", ")
            }

            writeLine()
            write("__metadata('design:returntype', ")
            emitSerializedReturnTypeOfNode(node)
            write(")")
            argumentsWritten++
          }
        }

        return argumentsWritten
      }

      def emitInterfaceDeclaration(node: InterfaceDeclaration) = {
        emitCommentsOnNotEmittedNode(node)
      }

      def shouldEmitEnumDeclaration(node: EnumDeclaration) = {
        val isConstEnum = isConst(node)
        return !isConstEnum || compilerOptions.preserveConstEnums || compilerOptions.isolatedModules
      }

      def emitEnumDeclaration(node: EnumDeclaration) = {
        // val enums are completely erased during compilation.
        if (!shouldEmitEnumDeclaration(node)) {
          return
        }

        if (!shouldHoistDeclarationInSystemJsModule(node)) {
          // do not emit var if variable was already hoisted

          val isES6ExportedEnum = isES6ExportedDeclaration(node)
          if (!(node.flags & NodeFlags.Export) || (isES6ExportedEnum && isFirstDeclarationOfKind(node, node.symbol && node.symbol.declarations, SyntaxKind.EnumDeclaration))) {
            emitStart(node)
            if (isES6ExportedEnum) {
              write("export ")
            }
            write("var ")
            emit(node.name)
            emitEnd(node)
            write(";")
          }
        }
        writeLine()
        emitStart(node)
        write("(def (")
        emitStart(node.name)
        write(getGeneratedNameForNode(node))
        emitEnd(node.name)
        write(") {")
        increaseIndent()
        emitLines(node.members)
        decreaseIndent()
        writeLine()
        emitToken(SyntaxKind.CloseBraceToken, node.members.end)
        write(")(")
        emitModuleMemberName(node)
        write(" || (")
        emitModuleMemberName(node)
        write(" = {}));")
        emitEnd(node)
        if (!isES6ExportedDeclaration(node) && node.flags & NodeFlags.Export && !shouldHoistDeclarationInSystemJsModule(node)) {
          // do not emit var if variable was already hoisted
          writeLine()
          emitStart(node)
          write("var ")
          emit(node.name)
          write(" = ")
          emitModuleMemberName(node)
          emitEnd(node)
          write(";")
        }
        if (modulekind != ModuleKind.ES6 && node.parent == currentSourceFile) {
          if (modulekind == ModuleKind.System && (node.flags & NodeFlags.Export)) {
            // write the call to exporter for enum
            writeLine()
            write(`${exportFunctionForFile}("`)
            emitDeclarationName(node)
            write(`", `)
            emitDeclarationName(node)
            write(");")
          }
          emitExportMemberAssignments(node.name)
        }
      }

      def emitEnumMember(node: EnumMember) = {
        val enumParent = <EnumDeclaration>node.parent
        emitStart(node)
        write(getGeneratedNameForNode(enumParent))
        write("[")
        write(getGeneratedNameForNode(enumParent))
        write("[")
        emitExpressionForPropertyName(node.name)
        write("] = ")
        writeEnumMemberDeclarationValue(node)
        write("] = ")
        emitExpressionForPropertyName(node.name)
        emitEnd(node)
        write(";")
      }

      def writeEnumMemberDeclarationValue(member: EnumMember) = {
        val value = resolver.getConstantValue(member)
        if (value != ()) {
          write(value.toString())
          return
        }
        else if (member.initializer) {
          emit(member.initializer)
        }
        else {
          write("()")
        }
      }

      def getInnerMostModuleDeclarationFromDottedModule(moduleDeclaration: ModuleDeclaration): ModuleDeclaration = {
        if (moduleDeclaration.body.kind == SyntaxKind.ModuleDeclaration) {
          val recursiveInnerModule = getInnerMostModuleDeclarationFromDottedModule(<ModuleDeclaration>moduleDeclaration.body)
          return recursiveInnerModule || <ModuleDeclaration>moduleDeclaration.body
        }
      }

      def shouldEmitModuleDeclaration(node: ModuleDeclaration) = {
        return isInstantiatedModule(node, compilerOptions.preserveConstEnums || compilerOptions.isolatedModules)
      }

      def isModuleMergedWithES6Class(node: ModuleDeclaration) = {
        return languageVersion == ScriptTarget.ES6 && !!(resolver.getNodeCheckFlags(node) & NodeCheckFlags.LexicalModuleMergesWithClass)
      }

      def isFirstDeclarationOfKind(node: Declaration, declarations: Declaration[], kind: SyntaxKind) = {
        return !forEach(declarations, declaration => declaration.kind == kind && declaration.pos < node.pos)
      }

      def emitModuleDeclaration(node: ModuleDeclaration) = {
        // Emit only if this module is non-ambient.
        val shouldEmit = shouldEmitModuleDeclaration(node)

        if (!shouldEmit) {
          return emitCommentsOnNotEmittedNode(node)
        }
        val hoistedInDeclarationScope = shouldHoistDeclarationInSystemJsModule(node)
        val emitVarForModule = !hoistedInDeclarationScope && !isModuleMergedWithES6Class(node)

        if (emitVarForModule) {
          val isES6ExportedNamespace = isES6ExportedDeclaration(node)
          if (!isES6ExportedNamespace || isFirstDeclarationOfKind(node, node.symbol && node.symbol.declarations, SyntaxKind.ModuleDeclaration)) {
            emitStart(node)
            if (isES6ExportedNamespace) {
              write("export ")
            }
            write("var ")
            emit(node.name)
            write(";")
            emitEnd(node)
            writeLine()
          }
        }

        emitStart(node)
        write("(def (")
        emitStart(node.name)
        write(getGeneratedNameForNode(node))
        emitEnd(node.name)
        write(") ")
        if (node.body.kind == SyntaxKind.ModuleBlock) {
          val saveConvertedLoopState = convertedLoopState
          val saveTempFlags = tempFlags
          val saveTempVariables = tempVariables
          convertedLoopState = ()
          tempFlags = 0
          tempVariables = ()

          emit(node.body)

          Debug.assert(convertedLoopState == ())
          convertedLoopState = saveConvertedLoopState

          tempFlags = saveTempFlags
          tempVariables = saveTempVariables
        }
        else {
          write("{")
          increaseIndent()
          emitCaptureThisForNodeIfNecessary(node)
          writeLine()
          emit(node.body)
          decreaseIndent()
          writeLine()
          val moduleBlock = <ModuleBlock>getInnerMostModuleDeclarationFromDottedModule(node).body
          emitToken(SyntaxKind.CloseBraceToken, moduleBlock.statements.end)
        }
        write(")(")
        // write moduleDecl = containingModule.m only if it is not exported es6 module member
        if ((node.flags & NodeFlags.Export) && !isES6ExportedDeclaration(node)) {
          emit(node.name)
          write(" = ")
        }
        emitModuleMemberName(node)
        write(" || (")
        emitModuleMemberName(node)
        write(" = {}));")
        emitEnd(node)
        if (!isES6ExportedDeclaration(node) && node.name.kind == SyntaxKind.Identifier && node.parent == currentSourceFile) {
          if (modulekind == ModuleKind.System && (node.flags & NodeFlags.Export)) {
            writeLine()
            write(`${exportFunctionForFile}("`)
            emitDeclarationName(node)
            write(`", `)
            emitDeclarationName(node)
            write(");")
          }
          emitExportMemberAssignments(<Identifier>node.name)
        }
      }

      /*
       * Some bundlers (SystemJS builder) sometimes want to rename dependencies.
       * Here we check if alternative name was provided for a given moduleName and return it if possible.
       */
      def tryRenameExternalModule(moduleName: LiteralExpression): String = {
        if (renamedDependencies && hasProperty(renamedDependencies, moduleName.text)) {
          return `"${renamedDependencies[moduleName.text]}"`
        }
        return ()
      }

      def emitRequire(moduleName: Expression) = {
        if (moduleName.kind == SyntaxKind.StringLiteral) {
          write("require(")
          val text = tryRenameExternalModule(<LiteralExpression>moduleName)
          if (text) {
            write(text)
          }
          else {
            emitStart(moduleName)
            emitLiteral(<LiteralExpression>moduleName)
            emitEnd(moduleName)
          }
          emitToken(SyntaxKind.CloseParenToken, moduleName.end)
        }
        else {
          write("require()")
        }
      }

      def getNamespaceDeclarationNode(node: ImportDeclaration | ImportEqualsDeclaration | ExportDeclaration) = {
        if (node.kind == SyntaxKind.ImportEqualsDeclaration) {
          return <ImportEqualsDeclaration>node
        }
        val importClause = (<ImportDeclaration>node).importClause
        if (importClause && importClause.namedBindings && importClause.namedBindings.kind == SyntaxKind.NamespaceImport) {
          return <NamespaceImport>importClause.namedBindings
        }
      }

      def isDefaultImport(node: ImportDeclaration | ImportEqualsDeclaration | ExportDeclaration) = {
        return node.kind == SyntaxKind.ImportDeclaration && (<ImportDeclaration>node).importClause && !!(<ImportDeclaration>node).importClause.name
      }

      def emitExportImportAssignments(node: Node) = {
        if (isAliasSymbolDeclaration(node) && resolver.isValueAliasDeclaration(node)) {
          emitExportMemberAssignments(<Identifier>(<Declaration>node).name)
        }
        forEachChild(node, emitExportImportAssignments)
      }

      def emitImportDeclaration(node: ImportDeclaration) = {
        if (modulekind != ModuleKind.ES6) {
          return emitExternalImportDeclaration(node)
        }

        // ES6 import
        if (node.importClause) {
          val shouldEmitDefaultBindings = resolver.isReferencedAliasDeclaration(node.importClause)
          val shouldEmitNamedBindings = node.importClause.namedBindings && resolver.isReferencedAliasDeclaration(node.importClause.namedBindings, /* checkChildren */ true)
          if (shouldEmitDefaultBindings || shouldEmitNamedBindings) {
            write("import ")
            emitStart(node.importClause)
            if (shouldEmitDefaultBindings) {
              emit(node.importClause.name)
              if (shouldEmitNamedBindings) {
                write(", ")
              }
            }
            if (shouldEmitNamedBindings) {
              emitLeadingComments(node.importClause.namedBindings)
              emitStart(node.importClause.namedBindings)
              if (node.importClause.namedBindings.kind == SyntaxKind.NamespaceImport) {
                write("* as ")
                emit((<NamespaceImport>node.importClause.namedBindings).name)
              }
              else {
                write("{ ")
                emitExportOrImportSpecifierList((<NamedImports>node.importClause.namedBindings).elements, resolver.isReferencedAliasDeclaration)
                write(" }")
              }
              emitEnd(node.importClause.namedBindings)
              emitTrailingComments(node.importClause.namedBindings)
            }

            emitEnd(node.importClause)
            write(" from ")
            emit(node.moduleSpecifier)
            write(";")
          }
        }
        else {
          write("import ")
          emit(node.moduleSpecifier)
          write(";")
        }
      }

      def emitExternalImportDeclaration(node: ImportDeclaration | ImportEqualsDeclaration) = {
        if (contains(externalImports, node)) {
          val isExportedImport = node.kind == SyntaxKind.ImportEqualsDeclaration && (node.flags & NodeFlags.Export) != 0
          val namespaceDeclaration = getNamespaceDeclarationNode(node)
          val varOrConst = (languageVersion <= ScriptTarget.ES5) ? "var " : "val "

          if (modulekind != ModuleKind.AMD) {
            emitLeadingComments(node)
            emitStart(node)
            if (namespaceDeclaration && !isDefaultImport(node)) {
              // import x = require("foo")
              // import * as x from "foo"
              if (!isExportedImport) {
                write(varOrConst)
              }
              emitModuleMemberName(namespaceDeclaration)
              write(" = ")
            }
            else {
              // import "foo"
              // import x from "foo"
              // import { x, y } from "foo"
              // import d, * as x from "foo"
              // import d, { x, y } from "foo"
              val isNakedImport = SyntaxKind.ImportDeclaration && !(<ImportDeclaration>node).importClause
              if (!isNakedImport) {
                write(varOrConst)
                write(getGeneratedNameForNode(<ImportDeclaration>node))
                write(" = ")
              }
            }
            emitRequire(getExternalModuleName(node))
            if (namespaceDeclaration && isDefaultImport(node)) {
              // import d, * as x from "foo"
              write(", ")
              emitModuleMemberName(namespaceDeclaration)
              write(" = ")
              write(getGeneratedNameForNode(<ImportDeclaration>node))
            }
            write(";")
            emitEnd(node)
            emitExportImportAssignments(node)
            emitTrailingComments(node)
          }
          else {
            if (isExportedImport) {
              emitModuleMemberName(namespaceDeclaration)
              write(" = ")
              emit(namespaceDeclaration.name)
              write(";")
            }
            else if (namespaceDeclaration && isDefaultImport(node)) {
              // import d, * as x from "foo"
              write(varOrConst)
              emitModuleMemberName(namespaceDeclaration)
              write(" = ")
              write(getGeneratedNameForNode(<ImportDeclaration>node))
              write(";")
            }
            emitExportImportAssignments(node)
          }
        }
      }

      def emitImportEqualsDeclaration(node: ImportEqualsDeclaration) = {
        if (isExternalModuleImportEqualsDeclaration(node)) {
          emitExternalImportDeclaration(node)
          return
        }
        // preserve old compiler's behavior: emit 'var' for import declaration (even if we do not consider them referenced) when
        // - current file is not external module
        // - import declaration is top level and target is value imported by entity name
        if (resolver.isReferencedAliasDeclaration(node) ||
          (!isCurrentFileExternalModule && resolver.isTopLevelValueImportEqualsWithEntityName(node))) {
          emitLeadingComments(node)
          emitStart(node)

          // variable declaration for import-equals declaration can be hoisted in system modules
          // in this case 'var' should be omitted and emit should contain only initialization
          val variableDeclarationIsHoisted = shouldHoistVariable(node, /*checkIfSourceFileLevelDecl*/ true)

          // is it top level import v = a.b.c in system module?
          // if yes - it needs to be rewritten as exporter('v', v = a.b.c)
          val isExported = isSourceFileLevelDeclarationInSystemJsModule(node, /*isExported*/ true)

          if (!variableDeclarationIsHoisted) {
            Debug.assert(!isExported)

            if (isES6ExportedDeclaration(node)) {
              write("export ")
              write("var ")
            }
            else if (!(node.flags & NodeFlags.Export)) {
              write("var ")
            }
          }


          if (isExported) {
            write(`${exportFunctionForFile}("`)
            emitNodeWithoutSourceMap(node.name)
            write(`", `)
          }

          emitModuleMemberName(node)
          write(" = ")
          emit(node.moduleReference)

          if (isExported) {
            write(")")
          }

          write(";")
          emitEnd(node)
          emitExportImportAssignments(node)
          emitTrailingComments(node)
        }
      }

      def emitExportDeclaration(node: ExportDeclaration) = {
        Debug.assert(modulekind != ModuleKind.System)

        if (modulekind != ModuleKind.ES6) {
          if (node.moduleSpecifier && (!node.exportClause || resolver.isValueAliasDeclaration(node))) {
            emitStart(node)
            val generatedName = getGeneratedNameForNode(node)
            if (node.exportClause) {
              // { x, y, ... } from "foo"
              if (modulekind != ModuleKind.AMD) {
                write("var ")
                write(generatedName)
                write(" = ")
                emitRequire(getExternalModuleName(node))
                write(";")
              }
              for (val specifier of node.exportClause.elements) {
                if (resolver.isValueAliasDeclaration(specifier)) {
                  writeLine()
                  emitStart(specifier)
                  emitContainingModuleName(specifier)
                  write(".")
                  emitNodeWithCommentsAndWithoutSourcemap(specifier.name)
                  write(" = ")
                  write(generatedName)
                  write(".")
                  emitNodeWithCommentsAndWithoutSourcemap(specifier.propertyName || specifier.name)
                  write(";")
                  emitEnd(specifier)
                }
              }
            }
            else {
              // * from "foo"
              if (hasExportStarsToExportValues && resolver.moduleExportsSomeValue(node.moduleSpecifier)) {
                writeLine()
                write("__export(")
                if (modulekind != ModuleKind.AMD) {
                  emitRequire(getExternalModuleName(node))
                }
                else {
                  write(generatedName)
                }
                write(");")
              }
            }
            emitEnd(node)
          }
        }
        else {
          if (!node.exportClause || resolver.isValueAliasDeclaration(node)) {
            write("export ")
            if (node.exportClause) {
              // { x, y, ... }
              write("{ ")
              emitExportOrImportSpecifierList(node.exportClause.elements, resolver.isValueAliasDeclaration)
              write(" }")
            }
            else {
              write("*")
            }
            if (node.moduleSpecifier) {
              write(" from ")
              emit(node.moduleSpecifier)
            }
            write(";")
          }
        }
      }

      def emitExportOrImportSpecifierList(specifiers: ImportOrExportSpecifier[], shouldEmit: (node: Node) => Boolean) = {
        Debug.assert(modulekind == ModuleKind.ES6)

        var needsComma = false
        for (val specifier of specifiers) {
          if (shouldEmit(specifier)) {
            if (needsComma) {
              write(", ")
            }
            if (specifier.propertyName) {
              emit(specifier.propertyName)
              write(" as ")
            }
            emit(specifier.name)
            needsComma = true
          }
        }
      }

      def emitExportAssignment(node: ExportAssignment) = {
        if (!node.isExportEquals && resolver.isValueAliasDeclaration(node)) {
          if (modulekind == ModuleKind.ES6) {
            writeLine()
            emitStart(node)
            write("export default ")
            val expression = node.expression
            emit(expression)
            if (expression.kind != SyntaxKind.FunctionDeclaration &&
              expression.kind != SyntaxKind.ClassDeclaration) {
              write(";")
            }
            emitEnd(node)
          }
          else {
            writeLine()
            emitStart(node)
            if (modulekind == ModuleKind.System) {
              write(`${exportFunctionForFile}("default",`)
              emit(node.expression)
              write(")")
            }
            else {
              emitEs6ExportDefaultCompat(node)
              emitContainingModuleName(node)
              if (languageVersion == ScriptTarget.ES3) {
                write("[\"default\"] = ")
              }
              else {
                write(".default = ")
              }
              emit(node.expression)
            }
            write(";")
            emitEnd(node)
          }
        }
      }

      def collectExternalModuleInfo(sourceFile: SourceFile) = {
        externalImports = []
        exportSpecifiers = {}
        exportEquals = ()
        hasExportStarsToExportValues = false
        for (val node of sourceFile.statements) {
          switch (node.kind) {
            case SyntaxKind.ImportDeclaration:
              if (!(<ImportDeclaration>node).importClause ||
                resolver.isReferencedAliasDeclaration((<ImportDeclaration>node).importClause, /*checkChildren*/ true)) {
                // import "mod"
                // import x from "mod" where x is referenced
                // import * as x from "mod" where x is referenced
                // import { x, y } from "mod" where at least one import is referenced
                externalImports.push(<ImportDeclaration>node)
              }
              break
            case SyntaxKind.ImportEqualsDeclaration:
              if ((<ImportEqualsDeclaration>node).moduleReference.kind == SyntaxKind.ExternalModuleReference && resolver.isReferencedAliasDeclaration(node)) {
                // import x = require("mod") where x is referenced
                externalImports.push(<ImportEqualsDeclaration>node)
              }
              break
            case SyntaxKind.ExportDeclaration:
              if ((<ExportDeclaration>node).moduleSpecifier) {
                if (!(<ExportDeclaration>node).exportClause) {
                  // * from "mod"
                  if (resolver.moduleExportsSomeValue((<ExportDeclaration>node).moduleSpecifier)) {
                    externalImports.push(<ExportDeclaration>node)
                    hasExportStarsToExportValues = true
                  }
                }
                else if (resolver.isValueAliasDeclaration(node)) {
                  // { x, y } from "mod" where at least one is a value symbol
                  externalImports.push(<ExportDeclaration>node)
                }
              }
              else {
                // { x, y }
                for (val specifier of (<ExportDeclaration>node).exportClause.elements) {
                  val name = (specifier.propertyName || specifier.name).text
                  (exportSpecifiers[name] || (exportSpecifiers[name] = [])).push(specifier)
                }
              }
              break
            case SyntaxKind.ExportAssignment:
              if ((<ExportAssignment>node).isExportEquals && !exportEquals) {
                // = x
                exportEquals = <ExportAssignment>node
              }
              break
          }
        }
      }

      def emitExportStarHelper() = {
        if (hasExportStarsToExportValues) {
          writeLine()
          write("def __export(m) {")
          increaseIndent()
          writeLine()
          write("for (var p in m) if (!exports.hasOwnProperty(p)) exports[p] = m[p];")
          decreaseIndent()
          writeLine()
          write("}")
        }
      }

      def getLocalNameForExternalImport(node: ImportDeclaration | ExportDeclaration | ImportEqualsDeclaration): String = {
        val namespaceDeclaration = getNamespaceDeclarationNode(node)
        if (namespaceDeclaration && !isDefaultImport(node)) {
          return getTextOfNodeFromSourceText(currentText, namespaceDeclaration.name)
        }
        if (node.kind == SyntaxKind.ImportDeclaration && (<ImportDeclaration>node).importClause) {
          return getGeneratedNameForNode(node)
        }
        if (node.kind == SyntaxKind.ExportDeclaration && (<ExportDeclaration>node).moduleSpecifier) {
          return getGeneratedNameForNode(node)
        }
      }

      def getExternalModuleNameText(importNode: ImportDeclaration | ExportDeclaration | ImportEqualsDeclaration, emitRelativePathAsModuleName: Boolean): String = {
        if (emitRelativePathAsModuleName) {
          val name = getExternalModuleNameFromDeclaration(host, resolver, importNode)
          if (name) {
            return `"${name}"`
          }
        }
        val moduleName = getExternalModuleName(importNode)
        if (moduleName.kind == SyntaxKind.StringLiteral) {
          return tryRenameExternalModule(<LiteralExpression>moduleName) || getLiteralText(<LiteralExpression>moduleName)
        }

        return ()
      }

      def emitVariableDeclarationsForImports(): Unit = {
        if (externalImports.length == 0) {
          return
        }

        writeLine()
        var started = false
        for (val importNode of externalImports) {
          // do not create variable declaration for exports and imports that lack import clause
          val skipNode =
            importNode.kind == SyntaxKind.ExportDeclaration ||
            (importNode.kind == SyntaxKind.ImportDeclaration && !(<ImportDeclaration>importNode).importClause)

          if (skipNode) {
            continue
          }

          if (!started) {
            write("var ")
            started = true
          }
          else {
            write(", ")
          }

          write(getLocalNameForExternalImport(importNode))
        }

        if (started) {
          write(";")
        }
      }

      def emitLocalStorageForExportedNamesIfNecessary(exportedDeclarations: (Identifier | Declaration)[]): String = {
        // when resolving exports local exported entries/indirect exported entries in the module
        // should always win over entries with similar names that were added via star exports
        // to support this we store names of local/indirect exported entries in a set.
        // this set is used to filter names brought by star exports.
        if (!hasExportStarsToExportValues) {
          // local names set is needed only in presence of star exports
          return ()
        }

        // local names set should only be added if we have anything exported
        if (!exportedDeclarations && isEmpty(exportSpecifiers)) {
          // no exported declarations (export var ...) or specifiers (export {x})
          // check if we have any non star declarations.
          var hasExportDeclarationWithExportClause = false
          for (val externalImport of externalImports) {
            if (externalImport.kind == SyntaxKind.ExportDeclaration && (<ExportDeclaration>externalImport).exportClause) {
              hasExportDeclarationWithExportClause = true
              break
            }
          }

          if (!hasExportDeclarationWithExportClause) {
            // we still need to emit exportStar helper
            return emitExportStarFunction(/*localNames*/ ())
          }
        }

        val exportedNamesStorageRef = makeUniqueName("exportedNames")

        writeLine()
        write(`var ${exportedNamesStorageRef} = {`)
        increaseIndent()

        var started = false
        if (exportedDeclarations) {
          for (var i = 0; i < exportedDeclarations.length; i++) {
            // write name of exported declaration, i.e 'export var x...'
            writeExportedName(exportedDeclarations[i])
          }
        }

        if (exportSpecifiers) {
          for (val n in exportSpecifiers) {
            for (val specifier of exportSpecifiers[n]) {
              // write name of specified, i.e. 'export {x}'
              writeExportedName(specifier.name)
            }
          }
        }

        for (val externalImport of externalImports) {
          if (externalImport.kind != SyntaxKind.ExportDeclaration) {
            continue
          }

          val exportDecl = <ExportDeclaration>externalImport
          if (!exportDecl.exportClause) {
            // * from ...
            continue
          }

          for (val element of exportDecl.exportClause.elements) {
            // write name of indirectly exported entry, i.e. 'export {x} from ...'
            writeExportedName(element.name || element.propertyName)
          }
        }

        decreaseIndent()
        writeLine()
        write("};")

        return emitExportStarFunction(exportedNamesStorageRef)

        def emitExportStarFunction(localNames: String): String = {
          val exportStarFunction = makeUniqueName("exportStar")

          writeLine()

          // define an star helper def
          write(`def ${exportStarFunction}(m) {`)
          increaseIndent()
          writeLine()
          write(`var exports = {};`)
          writeLine()
          write(`for(var n in m) {`)
          increaseIndent()
          writeLine()
          write(`if (n != "default"`)
          if (localNames) {
            write(`&& !${localNames}.hasOwnProperty(n)`)
          }
          write(`) exports[n] = m[n];`)
          decreaseIndent()
          writeLine()
          write("}")
          writeLine()
          write(`${exportFunctionForFile}(exports);`)
          decreaseIndent()
          writeLine()
          write("}")

          return exportStarFunction
        }

        def writeExportedName(node: Identifier | Declaration): Unit = {
          // do not record default exports
          // they are local to module and never overwritten (explicitly skipped) by star export
          if (node.kind != SyntaxKind.Identifier && node.flags & NodeFlags.Default) {
            return
          }

          if (started) {
            write(",")
          }
          else {
            started = true
          }

          writeLine()
          write("'")
          if (node.kind == SyntaxKind.Identifier) {
            emitNodeWithCommentsAndWithoutSourcemap(node)
          }
          else {
            emitDeclarationName(<Declaration>node)
          }

          write("': true")
        }
      }

      def processTopLevelVariableAndFunctionDeclarations(node: SourceFile): (Identifier | Declaration)[] {
        // per ES6 spec:
        // 15.2.1.16.4 ModuleDeclarationInstantiation() Concrete Method
        // - var declarations are initialized to () - 14.a.ii
        // - def/generator declarations are instantiated - 16.a.iv
        // this means that after module is instantiated but before its evaluation
        // exported functions are already accessible at import sites
        // in theory we should hoist only exported functions and its dependencies
        // in practice to simplify things we'll hoist all source level functions and variable declaration
        // including variables declarations for module and class declarations
        var hoistedVars: (Identifier | ClassDeclaration | ModuleDeclaration | EnumDeclaration)[]
        var hoistedFunctionDeclarations: FunctionDeclaration[]
        var exportedDeclarations: (Identifier | Declaration)[]

        visit(node)

        if (hoistedVars) {
          writeLine()
          write("var ")
          val seen: Map<String> = {}
          for (var i = 0; i < hoistedVars.length; i++) {
            val local = hoistedVars[i]
            val name = local.kind == SyntaxKind.Identifier
              ? <Identifier>local
              : <Identifier>(<ClassDeclaration | ModuleDeclaration | EnumDeclaration>local).name

            if (name) {
              // do not emit duplicate entries (in case of declaration merging) in the list of hoisted variables
              val text = unescapeIdentifier(name.text)
              if (hasProperty(seen, text)) {
                continue
              }
              else {
                seen[text] = text
              }
            }

            if (i != 0) {
              write(", ")
            }

            if (local.kind == SyntaxKind.ClassDeclaration || local.kind == SyntaxKind.ModuleDeclaration || local.kind == SyntaxKind.EnumDeclaration) {
              emitDeclarationName(<ClassDeclaration | ModuleDeclaration | EnumDeclaration>local)
            }
            else {
              emit(local)
            }

            val flags = getCombinedNodeFlags(local.kind == SyntaxKind.Identifier ? local.parent : local)
            if (flags & NodeFlags.Export) {
              if (!exportedDeclarations) {
                exportedDeclarations = []
              }
              exportedDeclarations.push(local)
            }
          }
          write(";")
        }

        if (hoistedFunctionDeclarations) {
          for (val f of hoistedFunctionDeclarations) {
            writeLine()
            emit(f)

            if (f.flags & NodeFlags.Export) {
              if (!exportedDeclarations) {
                exportedDeclarations = []
              }
              exportedDeclarations.push(f)
            }
          }
        }

        return exportedDeclarations

        def visit(node: Node): Unit = {
          if (node.flags & NodeFlags.Ambient) {
            return
          }

          if (node.kind == SyntaxKind.FunctionDeclaration) {
            if (!hoistedFunctionDeclarations) {
              hoistedFunctionDeclarations = []
            }

            hoistedFunctionDeclarations.push(<FunctionDeclaration>node)
            return
          }

          if (node.kind == SyntaxKind.ClassDeclaration) {
            if (!hoistedVars) {
              hoistedVars = []
            }

            hoistedVars.push(<ClassDeclaration>node)
            return
          }

          if (node.kind == SyntaxKind.EnumDeclaration) {
            if (shouldEmitEnumDeclaration(<EnumDeclaration>node)) {
              if (!hoistedVars) {
                hoistedVars = []
              }

              hoistedVars.push(<ModuleDeclaration>node)
            }

            return
          }

          if (node.kind == SyntaxKind.ModuleDeclaration) {
            if (shouldEmitModuleDeclaration(<ModuleDeclaration>node)) {
              if (!hoistedVars) {
                hoistedVars = []
              }

              hoistedVars.push(<ModuleDeclaration>node)
            }
            return
          }

          if (node.kind == SyntaxKind.VariableDeclaration || node.kind == SyntaxKind.BindingElement) {
            if (shouldHoistVariable(<VariableDeclaration | BindingElement>node, /*checkIfSourceFileLevelDecl*/ false)) {
              val name = (<VariableDeclaration | BindingElement>node).name
              if (name.kind == SyntaxKind.Identifier) {
                if (!hoistedVars) {
                  hoistedVars = []
                }

                hoistedVars.push(<Identifier>name)
              }
              else {
                forEachChild(name, visit)
              }
            }
            return
          }

          if (isInternalModuleImportEqualsDeclaration(node) && resolver.isValueAliasDeclaration(node)) {
            if (!hoistedVars) {
              hoistedVars = []
            }

            hoistedVars.push(node.name)
            return
          }

          if (isBindingPattern(node)) {
            forEach((<BindingPattern>node).elements, visit)
            return
          }

          if (!isDeclaration(node)) {
            forEachChild(node, visit)
          }
        }
      }

      def shouldHoistVariable(node: VariableDeclaration | VariableDeclarationList | BindingElement, checkIfSourceFileLevelDecl: Boolean): Boolean = {
        if (checkIfSourceFileLevelDecl && !shouldHoistDeclarationInSystemJsModule(node)) {
          return false
        }
        // hoist variable if
        // - it is not block scoped
        // - it is top level block scoped
        // if block scoped variables are nested in some another block then
        // no other functions can use them except ones that are defined at least in the same block
        return (getCombinedNodeFlags(node) & NodeFlags.BlockScoped) == 0 ||
          getEnclosingBlockScopeContainer(node).kind == SyntaxKind.SourceFile
      }

      def isCurrentFileSystemExternalModule() = {
        return modulekind == ModuleKind.System && isCurrentFileExternalModule
      }

      def emitSystemModuleBody(node: SourceFile, dependencyGroups: DependencyGroup[], startIndex: Int): Unit = {
        // shape of the body in system modules:
        // def (exports) {
        //   <list of local aliases for imports>
        //   <hoisted def declarations>
        //   <hoisted variable declarations>
        //   return {
        //     setters: [
        //       <list of setter def for imports>
        //     ],
        //     execute: def() {
        //       <module statements>
        //     }
        //   }
        //   <temp declarations>
        // }
        // I.e:
        // import {x} from 'file1'
        // var y = 1
        // def foo() { return y + x(); }
        // console.log(y)
        // will be transformed to
        // def(exports) {
        //   var file1; // local alias
        //   var y
        //   def foo() { return y + file1.x(); }
        //   exports("foo", foo)
        //   return {
        //     setters: [
        //       def(v) { file1 = v }
        //     ],
        //     execute(): def() {
        //       y = 1
        //       console.log(y)
        //     }
        //   }
        // }
        emitVariableDeclarationsForImports()
        writeLine()
        val exportedDeclarations = processTopLevelVariableAndFunctionDeclarations(node)
        val exportStarFunction = emitLocalStorageForExportedNamesIfNecessary(exportedDeclarations)
        writeLine()
        write("return {")
        increaseIndent()
        writeLine()
        emitSetters(exportStarFunction, dependencyGroups)
        writeLine()
        emitExecute(node, startIndex)
        decreaseIndent()
        writeLine()
        write("}"); // return
        emitTempDeclarations(/*newLine*/ true)
      }

      def emitSetters(exportStarFunction: String, dependencyGroups: DependencyGroup[]) = {
        write("setters:[")

        for (var i = 0; i < dependencyGroups.length; i++) {
          if (i != 0) {
            write(",")
          }

          writeLine()
          increaseIndent()

          val group = dependencyGroups[i]

          // derive a unique name for parameter from the first named entry in the group
          val parameterName = makeUniqueName(forEach(group, getLocalNameForExternalImport) || "")
          write(`def (${parameterName}) {`)
          increaseIndent()

          for (val entry of group) {
            val importVariableName = getLocalNameForExternalImport(entry) || ""

            switch (entry.kind) {
              case SyntaxKind.ImportDeclaration:
                if (!(<ImportDeclaration>entry).importClause) {
                  // 'import "..."' case
                  // module is imported only for side-effects, no emit required
                  break
                }
              // fall-through
              case SyntaxKind.ImportEqualsDeclaration:
                Debug.assert(importVariableName != "")

                writeLine()
                // save import into the local
                write(`${importVariableName} = ${parameterName};`)
                writeLine()
                break
              case SyntaxKind.ExportDeclaration:
                Debug.assert(importVariableName != "")

                if ((<ExportDeclaration>entry).exportClause) {
                  // {a, b as c} from 'foo'
                  // emit as:
                  // exports_({
                  //  "a": _["a"],
                  //  "c": _["b"]
                  // })
                  writeLine()
                  write(`${exportFunctionForFile}({`)
                  writeLine()
                  increaseIndent()
                  for (var i = 0, len = (<ExportDeclaration>entry).exportClause.elements.length; i < len; i++) {
                    if (i != 0) {
                      write(",")
                      writeLine()
                    }

                    val e = (<ExportDeclaration>entry).exportClause.elements[i]
                    write(`"`)
                    emitNodeWithCommentsAndWithoutSourcemap(e.name)
                    write(`": ${parameterName}["`)
                    emitNodeWithCommentsAndWithoutSourcemap(e.propertyName || e.name)
                    write(`"]`)
                  }
                  decreaseIndent()
                  writeLine()
                  write("});")
                }
                else {
                  // collectExternalModuleInfo prefilters star exports to keep only ones that values
                  // this means that check 'resolver.moduleExportsSomeValue' is redundant and can be omitted here
                  writeLine()
                  // * from 'foo'
                  // emit as:
                  // exportStar(_foo)
                  write(`${exportStarFunction}(${parameterName});`)
                }

                writeLine()
                break
            }

          }

          decreaseIndent()

          write("}")
          decreaseIndent()
        }
        write("],")
      }

      def emitExecute(node: SourceFile, startIndex: Int) = {
        write("execute: def() {")
        increaseIndent()
        writeLine()
        for (var i = startIndex; i < node.statements.length; i++) {
          val statement = node.statements[i]
          switch (statement.kind) {
            // - def declarations are not emitted because they were already hoisted
            // - import declarations are not emitted since they are already handled in setters
            // - declarations with module specifiers are not emitted since they were already written in setters
            // - declarations without module specifiers are emitted preserving the order
            case SyntaxKind.FunctionDeclaration:
            case SyntaxKind.ImportDeclaration:
              continue
            case SyntaxKind.ExportDeclaration:
              if (!(<ExportDeclaration>statement).moduleSpecifier) {
                for (val element of (<ExportDeclaration>statement).exportClause.elements) {
                  // write call to exporter def for every specifier in exports list
                  emitExportSpecifierInSystemModule(element)
                }
              }
              continue
            case SyntaxKind.ImportEqualsDeclaration:
              if (!isInternalModuleImportEqualsDeclaration(statement)) {
                // - import equals declarations that import external modules are not emitted
                continue
              }
              // fall-though for import declarations that import internal modules
            default:
              writeLine()
              emit(statement)
          }
        }
        decreaseIndent()
        writeLine()
        write("}"); // execute
      }

      def writeModuleName(node: SourceFile, emitRelativePathAsModuleName?: Boolean): Unit = {
        var moduleName = node.moduleName
        if (moduleName || (emitRelativePathAsModuleName && (moduleName = getResolvedExternalModuleName(host, node)))) {
          write(`"${moduleName}", `)
        }
      }

      def emitSystemModule(node: SourceFile,  emitRelativePathAsModuleName?: Boolean): Unit = {
        collectExternalModuleInfo(node)
        // System modules has the following shape
        // System.register(['dep-1', ... 'dep-n'], def(exports) {/* module body def */})
        // 'exports' here is a def 'exports<T>(name: String, value: T): T' that is used to publish exported values.
        // 'exports' returns its 'value' argument so in most cases expressions
        // that mutate exported values can be rewritten as:
        // expr -> exports('name', expr).
        // The only exception in this rule is postfix unary operators,
        // see comment to 'emitPostfixUnaryExpression' for more details
        Debug.assert(!exportFunctionForFile)
        // make sure that  name of 'exports' def does not conflict with existing identifiers
        exportFunctionForFile = makeUniqueName("exports")
        contextObjectForFile = makeUniqueName("context")
        writeLine()
        write("System.register(")
        writeModuleName(node, emitRelativePathAsModuleName)
        write("[")

        val groupIndices: Map<Int> = {}
        val dependencyGroups: DependencyGroup[] = []

        for (var i = 0; i < externalImports.length; i++) {
          val text = getExternalModuleNameText(externalImports[i], emitRelativePathAsModuleName)
          if (text == ()) {
            continue
          }

          // text should be quoted String
          // for deduplication purposes in key remove leading and trailing quotes so 'a' and "a" will be considered the same
          val key = text.substr(1, text.length - 2)

          if (hasProperty(groupIndices, key)) {
            // deduplicate/group entries in dependency list by the dependency name
            val groupIndex = groupIndices[key]
            dependencyGroups[groupIndex].push(externalImports[i])
            continue
          }
          else {
            groupIndices[key] = dependencyGroups.length
            dependencyGroups.push([externalImports[i]])
          }

          if (i != 0) {
            write(", ")
          }

          write(text)
        }
        write(`], def(${exportFunctionForFile}, ${contextObjectForFile}) {`)
        writeLine()
        increaseIndent()
        val startIndex = emitDirectivePrologues(node.statements, /*startWithNewLine*/ true, /*ensureUseStrict*/ !compilerOptions.noImplicitUseStrict)
        writeLine()
        write(`var __moduleName = ${contextObjectForFile} && ${contextObjectForFile}.id;`)
        writeLine()
        emitEmitHelpers(node)
        emitCaptureThisForNodeIfNecessary(node)
        emitSystemModuleBody(node, dependencyGroups, startIndex)
        decreaseIndent()
        writeLine()
        write("});")
      }

      trait AMDDependencyNames {
        aliasedModuleNames: String[]
        unaliasedModuleNames: String[]
        importAliasNames: String[]
      }

      def getAMDDependencyNames(node: SourceFile, includeNonAmdDependencies: Boolean, emitRelativePathAsModuleName?: Boolean): AMDDependencyNames = {
        // names of modules with corresponding parameter in the factory def
        val aliasedModuleNames: String[] = []
        // names of modules with no corresponding parameters in factory def
        val unaliasedModuleNames: String[] = []
        val importAliasNames: String[] = [];   // names of the parameters in the factory def; these
        // parameters need to match the indexes of the corresponding
        // module names in aliasedModuleNames.

        // Fill in amd-dependency tags
        for (val amdDependency of node.amdDependencies) {
          if (amdDependency.name) {
            aliasedModuleNames.push("\"" + amdDependency.path + "\"")
            importAliasNames.push(amdDependency.name)
          }
          else {
            unaliasedModuleNames.push("\"" + amdDependency.path + "\"")
          }
        }

        for (val importNode of externalImports) {
          // Find the name of the external module
          val externalModuleName = getExternalModuleNameText(importNode, emitRelativePathAsModuleName)

          // Find the name of the module alias, if there is one
          val importAliasName = getLocalNameForExternalImport(importNode)
          if (includeNonAmdDependencies && importAliasName) {
            aliasedModuleNames.push(externalModuleName)
            importAliasNames.push(importAliasName)
          }
          else {
            unaliasedModuleNames.push(externalModuleName)
          }
        }

        return { aliasedModuleNames, unaliasedModuleNames, importAliasNames }
      }

      def emitAMDDependencies(node: SourceFile, includeNonAmdDependencies: Boolean, emitRelativePathAsModuleName?: Boolean) = {
        // An AMD define def has the following shape:
        //   define(id?, dependencies?, factory)
        //
        // This has the shape of
        //   define(name, ["module1", "module2"], def (module1Alias) {
        // The location of the alias in the parameter list in the factory def needs to
        // match the position of the module name in the dependency list.
        //
        // To ensure this is true in cases of modules with no aliases, e.g.:
        // `import "module"` or `<amd-dependency path= "a.css" />`
        // we need to add modules without alias names to the end of the dependencies list

        val dependencyNames = getAMDDependencyNames(node, includeNonAmdDependencies, emitRelativePathAsModuleName)
        emitAMDDependencyList(dependencyNames)
        write(", ")
        emitAMDFactoryHeader(dependencyNames)
      }

      def emitAMDDependencyList({ aliasedModuleNames, unaliasedModuleNames }: AMDDependencyNames) = {
        write("[\"require\", \"exports\"")
        if (aliasedModuleNames.length) {
          write(", ")
          write(aliasedModuleNames.join(", "))
        }
        if (unaliasedModuleNames.length) {
          write(", ")
          write(unaliasedModuleNames.join(", "))
        }
        write("]")
      }

      def emitAMDFactoryHeader({ importAliasNames }: AMDDependencyNames) = {
        write("def (require, exports")
        if (importAliasNames.length) {
          write(", ")
          write(importAliasNames.join(", "))
        }
        write(") {")
      }

      def emitAMDModule(node: SourceFile, emitRelativePathAsModuleName?: Boolean) = {
        emitEmitHelpers(node)
        collectExternalModuleInfo(node)

        writeLine()
        write("define(")
        writeModuleName(node, emitRelativePathAsModuleName)
        emitAMDDependencies(node, /*includeNonAmdDependencies*/ true, emitRelativePathAsModuleName)
        increaseIndent()
        val startIndex = emitDirectivePrologues(node.statements, /*startWithNewLine*/ true, /*ensureUseStrict*/!compilerOptions.noImplicitUseStrict)
        emitExportStarHelper()
        emitCaptureThisForNodeIfNecessary(node)
        emitLinesStartingAt(node.statements, startIndex)
        emitTempDeclarations(/*newLine*/ true)
        emitExportEquals(/*emitAsReturn*/ true)
        decreaseIndent()
        writeLine()
        write("});")
      }

      def emitCommonJSModule(node: SourceFile) = {
        val startIndex = emitDirectivePrologues(node.statements, /*startWithNewLine*/ false, /*ensureUseStrict*/ !compilerOptions.noImplicitUseStrict)
        emitEmitHelpers(node)
        collectExternalModuleInfo(node)
        emitExportStarHelper()
        emitCaptureThisForNodeIfNecessary(node)
        emitLinesStartingAt(node.statements, startIndex)
        emitTempDeclarations(/*newLine*/ true)
        emitExportEquals(/*emitAsReturn*/ false)
      }

      def emitUMDModule(node: SourceFile) = {
        emitEmitHelpers(node)
        collectExternalModuleInfo(node)

        val dependencyNames = getAMDDependencyNames(node, /*includeNonAmdDependencies*/ false)

        // Module is detected first to support Browserify users that load into a browser with an AMD loader
        writeLines(`(def (factory) {
  if (typeof module == 'object' && typeof module.exports == 'object') {
    var v = factory(require, exports); if (v != ()) module.exports = v
  }
  else if (typeof define == 'def' && define.amd) {
    define(`)
        emitAMDDependencyList(dependencyNames)
        write(", factory);")
        writeLines(`  }
})(`)
        emitAMDFactoryHeader(dependencyNames)
        increaseIndent()
        val startIndex = emitDirectivePrologues(node.statements, /*startWithNewLine*/ true, /*ensureUseStrict*/ !compilerOptions.noImplicitUseStrict)
        emitExportStarHelper()
        emitCaptureThisForNodeIfNecessary(node)
        emitLinesStartingAt(node.statements, startIndex)
        emitTempDeclarations(/*newLine*/ true)
        emitExportEquals(/*emitAsReturn*/ true)
        decreaseIndent()
        writeLine()
        write("});")
      }

      def emitES6Module(node: SourceFile) = {
        externalImports = ()
        exportSpecifiers = ()
        exportEquals = ()
        hasExportStarsToExportValues = false
        val startIndex = emitDirectivePrologues(node.statements, /*startWithNewLine*/ false)
        emitEmitHelpers(node)
        emitCaptureThisForNodeIfNecessary(node)
        emitLinesStartingAt(node.statements, startIndex)
        emitTempDeclarations(/*newLine*/ true)
        // Emit exportDefault if it exists will happen as part
        // or normal statement emit.
      }

      def emitExportEquals(emitAsReturn: Boolean) = {
        if (exportEquals && resolver.isValueAliasDeclaration(exportEquals)) {
          writeLine()
          emitStart(exportEquals)
          write(emitAsReturn ? "return " : "module.exports = ")
          emit((<ExportAssignment>exportEquals).expression)
          write(";")
          emitEnd(exportEquals)
        }
      }

      def emitJsxElement(node: JsxElement | JsxSelfClosingElement) = {
        switch (compilerOptions.jsx) {
          case JsxEmit.React:
            jsxEmitReact(node)
            break
          case JsxEmit.Preserve:
          // Fall back to preserve if None was specified (we'll error earlier)
          default:
            jsxEmitPreserve(node)
            break
        }
      }

      def trimReactWhitespaceAndApplyEntities(node: JsxText): String = {
        var result: String = ()
        val text = getTextOfNode(node, /*includeTrivia*/ true)
        var firstNonWhitespace = 0
        var lastNonWhitespace = -1

        // JSX trims whitespace at the end and beginning of lines, except that the
        // start/end of a tag is considered a start/end of a line only if that line is
        // on the same line as the closing tag. See examples in tests/cases/conformance/jsx/tsxReactEmitWhitespace.tsx
        for (var i = 0; i < text.length; i++) {
          val c = text.charCodeAt(i)
          if (isLineBreak(c)) {
            if (firstNonWhitespace != -1 && (lastNonWhitespace - firstNonWhitespace + 1 > 0)) {
              val part = text.substr(firstNonWhitespace, lastNonWhitespace - firstNonWhitespace + 1)
              result = (result ? result + "\" + ' ' + \"" : "") + escapeString(part)
            }
            firstNonWhitespace = -1
          }
          else if (!isWhiteSpace(c)) {
            lastNonWhitespace = i
            if (firstNonWhitespace == -1) {
              firstNonWhitespace = i
            }
          }
        }

        if (firstNonWhitespace != -1) {
          val part = text.substr(firstNonWhitespace)
          result = (result ? result + "\" + ' ' + \"" : "") + escapeString(part)
        }

        if (result) {
          // Replace entities like &nbsp
          result = result.replace(/&(\w+);/g, def(s: any, m: String) {
            if (entities[m] != ()) {
              val ch = String.fromCharCode(entities[m])
              // &quot; needs to be escaped
              return ch == "\"" ? "\\\"" : ch
            }
            else {
              return s
            }
          })
        }

        return result
      }

      def isJsxChildEmittable(child: JsxChild): Boolean  {
        if (child.kind == SyntaxKind.JsxExpression) {
          // Don't emit empty expressions
          return !!(<JsxExpression>child).expression

        }
        else if (child.kind == SyntaxKind.JsxText) {
          // Don't emit empty strings
          return !!getTextToEmit(<JsxText>child)
        }

        return true
      }

      def getTextToEmit(node: JsxText): String = {
        switch (compilerOptions.jsx) {
          case JsxEmit.React:
            var text = trimReactWhitespaceAndApplyEntities(node)
            if (text == () || text.length == 0) {
              return ()
            }
            else {
              return text
            }
          case JsxEmit.Preserve:
          default:
            return getTextOfNode(node, /*includeTrivia*/ true)
        }
      }

      def emitJsxText(node: JsxText) = {
        switch (compilerOptions.jsx) {
          case JsxEmit.React:
            write("\"")
            write(trimReactWhitespaceAndApplyEntities(node))
            write("\"")
            break

          case JsxEmit.Preserve:
          default: // Emit JSX-preserve as default when no --jsx flag is specified
            writer.writeLiteral(getTextOfNode(node, /*includeTrivia*/ true))
            break
        }
      }

      def emitJsxExpression(node: JsxExpression) = {
        if (node.expression) {
          switch (compilerOptions.jsx) {
            case JsxEmit.Preserve:
            default:
              write("{")
              emit(node.expression)
              write("}")
              break
            case JsxEmit.React:
              emit(node.expression)
              break
          }
        }
      }

      def isUseStrictPrologue(node: ExpressionStatement): Boolean = {
        return !!(node.expression as StringLiteral).text.match(/use strict/)
      }

      def ensureUseStrictPrologue(startWithNewLine: Boolean, writeUseStrict: Boolean) = {
        if (writeUseStrict) {
          if (startWithNewLine) {
            writeLine()
          }
          write("\"use strict\";")
        }
      }

      def emitDirectivePrologues(statements: Node[], startWithNewLine: Boolean, ensureUseStrict?: Boolean): Int = {
        var foundUseStrict = false
        for (var i = 0; i < statements.length; i++) {
          if (isPrologueDirective(statements[i])) {
            if (isUseStrictPrologue(statements[i] as ExpressionStatement)) {
              foundUseStrict = true
            }
            if (startWithNewLine || i > 0) {
              writeLine()
            }
            emit(statements[i])
          }
          else {
            ensureUseStrictPrologue(startWithNewLine || i > 0, !foundUseStrict && ensureUseStrict)
            // return index of the first non prologue directive
            return i
          }
        }
        ensureUseStrictPrologue(startWithNewLine, !foundUseStrict && ensureUseStrict)
        return statements.length
      }

      def writeLines(text: String): Unit = {
        val lines = text.split(/\r\n|\r|\n/g)
        for (var i = 0; i < lines.length; i++) {
          val line = lines[i]
          if (line.length) {
            writeLine()
            write(line)
          }
        }
      }

      def emitEmitHelpers(node: SourceFile): Unit = {
        // Only emit helpers if the user did not say otherwise.
        if (!compilerOptions.noEmitHelpers) {
          // Only Emit __extends def when target ES5.
          // For target ES6 and above, we can emit classDeclaration as is.
          if ((languageVersion < ScriptTarget.ES6) && (!extendsEmitted && node.flags & NodeFlags.HasClassExtends)) {
            writeLines(extendsHelper)
            extendsEmitted = true
          }

          if (!decorateEmitted && node.flags & NodeFlags.HasDecorators) {
            writeLines(decorateHelper)
            if (compilerOptions.emitDecoratorMetadata) {
              writeLines(metadataHelper)
            }
            decorateEmitted = true
          }

          if (!paramEmitted && node.flags & NodeFlags.HasParamDecorators) {
            writeLines(paramHelper)
            paramEmitted = true
          }

          if (!awaiterEmitted && node.flags & NodeFlags.HasAsyncFunctions) {
            writeLines(awaiterHelper)
            awaiterEmitted = true
          }
        }
      }

      def emitSourceFileNode(node: SourceFile) = {
        // Start new file on new line
        writeLine()
        emitShebang()
        emitDetachedCommentsAndUpdateCommentsInfo(node)

        if (isExternalModule(node) || compilerOptions.isolatedModules) {
          if (isOwnFileEmit || (!isExternalModule(node) && compilerOptions.isolatedModules)) {
            val emitModule = moduleEmitDelegates[modulekind] || moduleEmitDelegates[ModuleKind.CommonJS]
            emitModule(node)
          }
          else {
            bundleEmitDelegates[modulekind](node, /*emitRelativePathAsModuleName*/true)
          }
        }
        else {
          // emit prologue directives prior to __extends
          val startIndex = emitDirectivePrologues(node.statements, /*startWithNewLine*/ false)
          externalImports = ()
          exportSpecifiers = ()
          exportEquals = ()
          hasExportStarsToExportValues = false
          emitEmitHelpers(node)
          emitCaptureThisForNodeIfNecessary(node)
          emitLinesStartingAt(node.statements, startIndex)
          emitTempDeclarations(/*newLine*/ true)
        }

        emitLeadingComments(node.endOfFileToken)
      }

      def emit(node: Node): Unit = {
        emitNodeConsideringCommentsOption(node, emitNodeWithSourceMap)
      }

      def emitNodeWithCommentsAndWithoutSourcemap(node: Node): Unit = {
        emitNodeConsideringCommentsOption(node, emitNodeWithoutSourceMap)
      }

      def emitNodeConsideringCommentsOption(node: Node, emitNodeConsideringSourcemap: (node: Node) => Unit): Unit = {
        if (node) {
          if (node.flags & NodeFlags.Ambient) {
            return emitCommentsOnNotEmittedNode(node)
          }

          if (isSpecializedCommentHandling(node)) {
            // This is the node that will handle its own comments and sourcemap
            return emitNodeWithoutSourceMap(node)
          }

          val emitComments = shouldEmitLeadingAndTrailingComments(node)
          if (emitComments) {
            emitLeadingComments(node)
          }

          emitNodeConsideringSourcemap(node)

          if (emitComments) {
            emitTrailingComments(node)
          }
        }
      }

      def emitNodeWithSourceMap(node: Node): Unit = {
        if (node) {
          emitStart(node)
          emitNodeWithoutSourceMap(node)
          emitEnd(node)
        }
      }

      def emitNodeWithoutSourceMap(node: Node): Unit = {
        if (node) {
          emitJavaScriptWorker(node)
        }
      }

      def changeSourceMapEmit(writer: SourceMapWriter) = {
        sourceMap = writer
        emitStart = writer.emitStart
        emitEnd = writer.emitEnd
        emitPos = writer.emitPos
        setSourceFile = writer.setSourceFile
      }

      def withTemporaryNoSourceMap(callback: () => Unit) = {
        val prevSourceMap = sourceMap
        setSourceMapWriterEmit(getNullSourceMapWriter())
        callback()
        setSourceMapWriterEmit(prevSourceMap)
      }

      def isSpecializedCommentHandling(node: Node): Boolean = {
        switch (node.kind) {
          // All of these entities are emitted in a specialized fashion.  As such, we allow
          // the specialized methods for each to handle the comments on the nodes.
          case SyntaxKind.InterfaceDeclaration:
          case SyntaxKind.FunctionDeclaration:
          case SyntaxKind.ImportDeclaration:
          case SyntaxKind.ImportEqualsDeclaration:
          case SyntaxKind.TypeAliasDeclaration:
          case SyntaxKind.ExportAssignment:
            return true
        }
      }

      def shouldEmitLeadingAndTrailingComments(node: Node) = {
        switch (node.kind) {
          case SyntaxKind.VariableStatement:
            return shouldEmitLeadingAndTrailingCommentsForVariableStatement(<VariableStatement>node)

          case SyntaxKind.ModuleDeclaration:
            // Only emit the leading/trailing comments for a module if we're actually
            // emitting the module as well.
            return shouldEmitModuleDeclaration(<ModuleDeclaration>node)

          case SyntaxKind.EnumDeclaration:
            // Only emit the leading/trailing comments for an enum if we're actually
            // emitting the module as well.
            return shouldEmitEnumDeclaration(<EnumDeclaration>node)
        }

        // If the node is emitted in specialized fashion, dont emit comments as this node will handle
        // emitting comments when emitting itself
        Debug.assert(!isSpecializedCommentHandling(node))

        // If this is the expression body of an arrow def that we're down-leveling,
        // then we don't want to emit comments when we emit the body.  It will have already
        // been taken care of when we emitted the 'return' statement for the def
        // expression body.
        if (node.kind != SyntaxKind.Block &&
          node.parent &&
          node.parent.kind == SyntaxKind.ArrowFunction &&
          (<ArrowFunction>node.parent).body == node &&
          compilerOptions.target <= ScriptTarget.ES5) {

          return false
        }

        // Emit comments for everything else.
        return true
      }

      def emitJavaScriptWorker(node: Node) = {
        // Check if the node can be emitted regardless of the ScriptTarget
        switch (node.kind) {
          case SyntaxKind.Identifier:
            return emitIdentifier(<Identifier>node)
          case SyntaxKind.Parameter:
            return emitParameter(<ParameterDeclaration>node)
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.MethodSignature:
            return emitMethod(<MethodDeclaration>node)
          case SyntaxKind.GetAccessor:
          case SyntaxKind.SetAccessor:
            return emitAccessor(<AccessorDeclaration>node)
          case SyntaxKind.ThisKeyword:
            return emitThis(node)
          case SyntaxKind.SuperKeyword:
            return emitSuper(node)
          case SyntaxKind.NullKeyword:
            return write("null")
          case SyntaxKind.TrueKeyword:
            return write("true")
          case SyntaxKind.FalseKeyword:
            return write("false")
          case SyntaxKind.NumericLiteral:
          case SyntaxKind.StringLiteral:
          case SyntaxKind.RegularExpressionLiteral:
          case SyntaxKind.NoSubstitutionTemplateLiteral:
          case SyntaxKind.TemplateHead:
          case SyntaxKind.TemplateMiddle:
          case SyntaxKind.TemplateTail:
            return emitLiteral(<LiteralExpression>node)
          case SyntaxKind.TemplateExpression:
            return emitTemplateExpression(<TemplateExpression>node)
          case SyntaxKind.TemplateSpan:
            return emitTemplateSpan(<TemplateSpan>node)
          case SyntaxKind.JsxElement:
          case SyntaxKind.JsxSelfClosingElement:
            return emitJsxElement(<JsxElement | JsxSelfClosingElement>node)
          case SyntaxKind.JsxText:
            return emitJsxText(<JsxText>node)
          case SyntaxKind.JsxExpression:
            return emitJsxExpression(<JsxExpression>node)
          case SyntaxKind.QualifiedName:
            return emitQualifiedName(<QualifiedName>node)
          case SyntaxKind.ObjectBindingPattern:
            return emitObjectBindingPattern(<BindingPattern>node)
          case SyntaxKind.ArrayBindingPattern:
            return emitArrayBindingPattern(<BindingPattern>node)
          case SyntaxKind.BindingElement:
            return emitBindingElement(<BindingElement>node)
          case SyntaxKind.ArrayLiteralExpression:
            return emitArrayLiteral(<ArrayLiteralExpression>node)
          case SyntaxKind.ObjectLiteralExpression:
            return emitObjectLiteral(<ObjectLiteralExpression>node)
          case SyntaxKind.PropertyAssignment:
            return emitPropertyAssignment(<PropertyDeclaration>node)
          case SyntaxKind.ShorthandPropertyAssignment:
            return emitShorthandPropertyAssignment(<ShorthandPropertyAssignment>node)
          case SyntaxKind.ComputedPropertyName:
            return emitComputedPropertyName(<ComputedPropertyName>node)
          case SyntaxKind.PropertyAccessExpression:
            return emitPropertyAccess(<PropertyAccessExpression>node)
          case SyntaxKind.ElementAccessExpression:
            return emitIndexedAccess(<ElementAccessExpression>node)
          case SyntaxKind.CallExpression:
            return emitCallExpression(<CallExpression>node)
          case SyntaxKind.NewExpression:
            return emitNewExpression(<NewExpression>node)
          case SyntaxKind.TaggedTemplateExpression:
            return emitTaggedTemplateExpression(<TaggedTemplateExpression>node)
          case SyntaxKind.TypeAssertionExpression:
            return emit((<TypeAssertion>node).expression)
          case SyntaxKind.AsExpression:
            return emit((<AsExpression>node).expression)
          case SyntaxKind.ParenthesizedExpression:
            return emitParenExpression(<ParenthesizedExpression>node)
          case SyntaxKind.FunctionDeclaration:
          case SyntaxKind.FunctionExpression:
          case SyntaxKind.ArrowFunction:
            return emitFunctionDeclaration(<FunctionLikeDeclaration>node)
          case SyntaxKind.DeleteExpression:
            return emitDeleteExpression(<DeleteExpression>node)
          case SyntaxKind.TypeOfExpression:
            return emitTypeOfExpression(<TypeOfExpression>node)
          case SyntaxKind.VoidExpression:
            return emitVoidExpression(<VoidExpression>node)
          case SyntaxKind.AwaitExpression:
            return emitAwaitExpression(<AwaitExpression>node)
          case SyntaxKind.PrefixUnaryExpression:
            return emitPrefixUnaryExpression(<PrefixUnaryExpression>node)
          case SyntaxKind.PostfixUnaryExpression:
            return emitPostfixUnaryExpression(<PostfixUnaryExpression>node)
          case SyntaxKind.BinaryExpression:
            return emitBinaryExpression(<BinaryExpression>node)
          case SyntaxKind.ConditionalExpression:
            return emitConditionalExpression(<ConditionalExpression>node)
          case SyntaxKind.SpreadElementExpression:
            return emitSpreadElementExpression(<SpreadElementExpression>node)
          case SyntaxKind.YieldExpression:
            return emitYieldExpression(<YieldExpression>node)
          case SyntaxKind.OmittedExpression:
            return
          case SyntaxKind.Block:
          case SyntaxKind.ModuleBlock:
            return emitBlock(<Block>node)
          case SyntaxKind.VariableStatement:
            return emitVariableStatement(<VariableStatement>node)
          case SyntaxKind.EmptyStatement:
            return write(";")
          case SyntaxKind.ExpressionStatement:
            return emitExpressionStatement(<ExpressionStatement>node)
          case SyntaxKind.IfStatement:
            return emitIfStatement(<IfStatement>node)
          case SyntaxKind.DoStatement:
            return emitDoStatement(<DoStatement>node)
          case SyntaxKind.WhileStatement:
            return emitWhileStatement(<WhileStatement>node)
          case SyntaxKind.ForStatement:
            return emitForStatement(<ForStatement>node)
          case SyntaxKind.ForOfStatement:
          case SyntaxKind.ForInStatement:
            return emitForInOrForOfStatement(<ForInStatement>node)
          case SyntaxKind.ContinueStatement:
          case SyntaxKind.BreakStatement:
            return emitBreakOrContinueStatement(<BreakOrContinueStatement>node)
          case SyntaxKind.ReturnStatement:
            return emitReturnStatement(<ReturnStatement>node)
          case SyntaxKind.WithStatement:
            return emitWithStatement(<WithStatement>node)
          case SyntaxKind.SwitchStatement:
            return emitSwitchStatement(<SwitchStatement>node)
          case SyntaxKind.CaseClause:
          case SyntaxKind.DefaultClause:
            return emitCaseOrDefaultClause(<CaseOrDefaultClause>node)
          case SyntaxKind.LabeledStatement:
            return emitLabeledStatement(<LabeledStatement>node)
          case SyntaxKind.ThrowStatement:
            return emitThrowStatement(<ThrowStatement>node)
          case SyntaxKind.TryStatement:
            return emitTryStatement(<TryStatement>node)
          case SyntaxKind.CatchClause:
            return emitCatchClause(<CatchClause>node)
          case SyntaxKind.DebuggerStatement:
            return emitDebuggerStatement(node)
          case SyntaxKind.VariableDeclaration:
            return emitVariableDeclaration(<VariableDeclaration>node)
          case SyntaxKind.ClassExpression:
            return emitClassExpression(<ClassExpression>node)
          case SyntaxKind.ClassDeclaration:
            return emitClassDeclaration(<ClassDeclaration>node)
          case SyntaxKind.InterfaceDeclaration:
            return emitInterfaceDeclaration(<InterfaceDeclaration>node)
          case SyntaxKind.EnumDeclaration:
            return emitEnumDeclaration(<EnumDeclaration>node)
          case SyntaxKind.EnumMember:
            return emitEnumMember(<EnumMember>node)
          case SyntaxKind.ModuleDeclaration:
            return emitModuleDeclaration(<ModuleDeclaration>node)
          case SyntaxKind.ImportDeclaration:
            return emitImportDeclaration(<ImportDeclaration>node)
          case SyntaxKind.ImportEqualsDeclaration:
            return emitImportEqualsDeclaration(<ImportEqualsDeclaration>node)
          case SyntaxKind.ExportDeclaration:
            return emitExportDeclaration(<ExportDeclaration>node)
          case SyntaxKind.ExportAssignment:
            return emitExportAssignment(<ExportAssignment>node)
          case SyntaxKind.SourceFile:
            return emitSourceFileNode(<SourceFile>node)
        }
      }

      def hasDetachedComments(pos: Int) = {
        return detachedCommentsInfo != () && lastOrUndefined(detachedCommentsInfo).nodePos == pos
      }

      def getLeadingCommentsWithoutDetachedComments() = {
        // get the leading comments from detachedPos
        val leadingComments = getLeadingCommentRanges(currentText,
          lastOrUndefined(detachedCommentsInfo).detachedCommentEndPos)
        if (detachedCommentsInfo.length - 1) {
          detachedCommentsInfo.pop()
        }
        else {
          detachedCommentsInfo = ()
        }

        return leadingComments
      }

      /**
       * Determine if the given comment is a triple-slash
       *
       * @return true if the comment is a triple-slash comment else false
       **/
      def isTripleSlashComment(comment: CommentRange) = {
        // Verify this is /// comment, but do the regexp match only when we first can find /// in the comment text
        // so that we don't end up computing comment String and doing match for all // comments
        if (currentText.charCodeAt(comment.pos + 1) == CharacterCodes.slash &&
          comment.pos + 2 < comment.end &&
          currentText.charCodeAt(comment.pos + 2) == CharacterCodes.slash) {
          val textSubStr = currentText.substring(comment.pos, comment.end)
          return textSubStr.match(fullTripleSlashReferencePathRegEx) ||
            textSubStr.match(fullTripleSlashAMDReferencePathRegEx) ?
            true : false
        }
        return false
      }

      def getLeadingCommentsToEmit(node: Node) = {
        // Emit the leading comments only if the parent's pos doesn't match because parent should take care of emitting these comments
        if (node.parent) {
          if (node.parent.kind == SyntaxKind.SourceFile || node.pos != node.parent.pos) {
            if (hasDetachedComments(node.pos)) {
              // get comments without detached comments
              return getLeadingCommentsWithoutDetachedComments()
            }
            else {
              // get the leading comments from the node
              return getLeadingCommentRangesOfNodeFromText(node, currentText)
            }
          }
        }
      }

      def getTrailingCommentsToEmit(node: Node) = {
        // Emit the trailing comments only if the parent's pos doesn't match because parent should take care of emitting these comments
        if (node.parent) {
          if (node.parent.kind == SyntaxKind.SourceFile || node.end != node.parent.end) {
            return getTrailingCommentRanges(currentText, node.end)
          }
        }
      }

      /**
       * Emit comments associated with node that will not be emitted into JS file
       */
      def emitCommentsOnNotEmittedNode(node: Node) = {
        emitLeadingCommentsWorker(node, /*isEmittedNode*/ false)
      }

      def emitLeadingComments(node: Node) = {
        return emitLeadingCommentsWorker(node, /*isEmittedNode*/ true)
      }

      def emitLeadingCommentsWorker(node: Node, isEmittedNode: Boolean) = {
        if (compilerOptions.removeComments) {
          return
        }

        var leadingComments: CommentRange[]
        if (isEmittedNode) {
          leadingComments = getLeadingCommentsToEmit(node)
        }
        else {
          // If the node will not be emitted in JS, remove all the comments(normal, pinned and ///) associated with the node,
          // unless it is a triple slash comment at the top of the file.
          // For Example:
          //    /// <reference-path ...>
          //    declare var x
          //    /// <reference-path ...>
          //    trait F {}
          //  The first /// will NOT be removed while the second one will be removed even though both node will not be emitted
          if (node.pos == 0) {
            leadingComments = filter(getLeadingCommentsToEmit(node), isTripleSlashComment)
          }
        }

        emitNewLineBeforeLeadingComments(currentLineMap, writer, node, leadingComments)

        // Leading comments are emitted at /*leading comment1 */space/*leading comment*/space
        emitComments(currentText, currentLineMap, writer, leadingComments, /*trailingSeparator*/ true, newLine, writeComment)
      }

      def emitTrailingComments(node: Node) = {
        if (compilerOptions.removeComments) {
          return
        }

        // Emit the trailing comments only if the parent's end doesn't match
        val trailingComments = getTrailingCommentsToEmit(node)

        // trailing comments are emitted at space/*trailing comment1 */space/*trailing comment*/
        emitComments(currentText, currentLineMap, writer, trailingComments, /*trailingSeparator*/ false, newLine, writeComment)
      }

      /**
       * Emit trailing comments at the position. The term trailing comment is used here to describe following comment:
       *    x, /comment1/ y
       *    ^ => pos; the def will emit "comment1" in the emitJS
       */
      def emitTrailingCommentsOfPosition(pos: Int) = {
        if (compilerOptions.removeComments) {
          return
        }

        val trailingComments = getTrailingCommentRanges(currentText, pos)

        // trailing comments are emitted at space/*trailing comment1 */space/*trailing comment*/
        emitComments(currentText, currentLineMap, writer, trailingComments, /*trailingSeparator*/ true, newLine, writeComment)
      }

      def emitLeadingCommentsOfPositionWorker(pos: Int) = {
        if (compilerOptions.removeComments) {
          return
        }

        var leadingComments: CommentRange[]
        if (hasDetachedComments(pos)) {
          // get comments without detached comments
          leadingComments = getLeadingCommentsWithoutDetachedComments()
        }
        else {
          // get the leading comments from the node
          leadingComments = getLeadingCommentRanges(currentText, pos)
        }

        emitNewLineBeforeLeadingComments(currentLineMap, writer, { pos: pos, end: pos }, leadingComments)

        // Leading comments are emitted at /*leading comment1 */space/*leading comment*/space
        emitComments(currentText, currentLineMap, writer, leadingComments, /*trailingSeparator*/ true, newLine, writeComment)
      }

      def emitDetachedCommentsAndUpdateCommentsInfo(node: TextRange) = {
        val currentDetachedCommentInfo = emitDetachedComments(currentText, currentLineMap, writer, writeComment, node, newLine, compilerOptions.removeComments)

        if (currentDetachedCommentInfo) {
          if (detachedCommentsInfo) {
            detachedCommentsInfo.push(currentDetachedCommentInfo)
          }
          else {
            detachedCommentsInfo = [currentDetachedCommentInfo]
          }
        }
      }

      def writeComment(text: String, lineMap: Int[], writer: EmitTextWriter, comment: CommentRange, newLine: String) = {
        emitPos(comment.pos)
        writeCommentRange(text, lineMap, writer, comment, newLine)
        emitPos(comment.end)
      }

      def emitShebang() = {
        val shebang = getShebang(currentText)
        if (shebang) {
          write(shebang)
          writeLine()
        }
      }
    }

    def emitFile({ jsFilePath, sourceMapFilePath, declarationFilePath}: { jsFilePath: String, sourceMapFilePath: String, declarationFilePath: String },
      sourceFiles: SourceFile[], isBundledEmit: Boolean) {
      // Make sure not to write js File and source map file if any of them cannot be written
      if (!host.isEmitBlocked(jsFilePath) && !compilerOptions.noEmit) {
        emitJavaScript(jsFilePath, sourceMapFilePath, sourceFiles, isBundledEmit)
      }
      else {
        emitSkipped = true
      }

      if (declarationFilePath) {
        emitSkipped = writeDeclarationFile(declarationFilePath, sourceFiles, isBundledEmit, host, resolver, emitterDiagnostics) || emitSkipped
      }
    }
  }
}
