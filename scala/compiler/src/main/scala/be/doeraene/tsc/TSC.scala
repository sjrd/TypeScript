package be.doeraene.tsc

/// <reference path="program.ts"/>
/// <reference path="commandLineParser.ts"/>

object TSC {
  trait SourceFile {
    fileWatcher?: FileWatcher
  }

  var reportDiagnostic = reportDiagnosticSimply

  def reportDiagnostics(diagnostics: Diagnostic[], host: CompilerHost): Unit {
    for (val diagnostic of diagnostics) {
      reportDiagnostic(diagnostic, host)
    }
  }

  /**
   * Checks to see if the locale is in the appropriate format,
   * and if it is, attempts to set the appropriate language.
   */
  def validateLocaleAndSetLanguage(locale: String, errors: Diagnostic[]): Boolean {
    val matchResult = /^([a-z]+)([_\-]([a-z]+))?$/.exec(locale.toLowerCase())

    if (!matchResult) {
      errors.push(createCompilerDiagnostic(Diagnostics.Locale_must_be_of_the_form_language_or_language_territory_For_example_0_or_1, "en", "ja-jp"))
      return false
    }

    val language = matchResult[1]
    val territory = matchResult[3]

    // First try the entire locale, then fall back to just language if that's all we have.
    if (!trySetLanguageAndTerritory(language, territory, errors) &&
      !trySetLanguageAndTerritory(language, (), errors)) {

      errors.push(createCompilerDiagnostic(Diagnostics.Unsupported_locale_0, locale))
      return false
    }

    return true
  }

  def trySetLanguageAndTerritory(language: String, territory: String, errors: Diagnostic[]): Boolean {
    val compilerFilePath = normalizePath(sys.getExecutingFilePath())
    val containingDirectoryPath = getDirectoryPath(compilerFilePath)

    var filePath = combinePaths(containingDirectoryPath, language)

    if (territory) {
      filePath = filePath + "-" + territory
    }

    filePath = sys.resolvePath(combinePaths(filePath, "diagnosticMessages.generated.json"))

    if (!sys.fileExists(filePath)) {
      return false
    }

    // TODO: Add codePage support for readFile?
    var fileContents = ""
    try {
      fileContents = sys.readFile(filePath)
    }
    catch (e) {
      errors.push(createCompilerDiagnostic(Diagnostics.Unable_to_open_file_0, filePath))
      return false
    }
    try {
      ts.localizedDiagnosticMessages = JSON.parse(fileContents)
    }
    catch (e) {
      errors.push(createCompilerDiagnostic(Diagnostics.Corrupted_locale_file_0, filePath))
      return false
    }

    return true
  }

  def countLines(program: Program): Int {
    var count = 0
    forEach(program.getSourceFiles(), file => {
      count += getLineStarts(file).length
    })
    return count
  }

  def getDiagnosticText(message: DiagnosticMessage, ...args: any[]): String {
    val diagnostic = createCompilerDiagnostic.apply((), arguments)
    return <String>diagnostic.messageText
  }

  def getRelativeFileName(fileName: String, host: CompilerHost): String {
    return host ? convertToRelativePath(fileName, host.getCurrentDirectory(), fileName => host.getCanonicalFileName(fileName)) : fileName
  }

  def reportDiagnosticSimply(diagnostic: Diagnostic, host: CompilerHost): Unit {
    var output = ""

    if (diagnostic.file) {
      val { line, character } = getLineAndCharacterOfPosition(diagnostic.file, diagnostic.start)
      val relativeFileName = getRelativeFileName(diagnostic.file.fileName, host)
      output += `${ relativeFileName }(${ line + 1 },${ character + 1 }): `
    }

    val category = DiagnosticCategory[diagnostic.category].toLowerCase()
    output += `${ category } TS${ diagnostic.code }: ${ flattenDiagnosticMessageText(diagnostic.messageText, sys.newLine) }${ sys.newLine }`

    sys.write(output)
  }


  val redForegroundEscapeSequence = "\u001b[91m"
  val yellowForegroundEscapeSequence = "\u001b[93m"
  val blueForegroundEscapeSequence = "\u001b[93m"
  val gutterStyleSequence = "\u001b[100;30m"
  val gutterSeparator = " "
  val resetEscapeSequence = "\u001b[0m"
  val ellipsis = "..."
  val categoryFormatMap: Map<String> = {
    [DiagnosticCategory.Warning]: yellowForegroundEscapeSequence,
    [DiagnosticCategory.Error]: redForegroundEscapeSequence,
    [DiagnosticCategory.Message]: blueForegroundEscapeSequence,
  }

  def formatAndReset(text: String, formatStyle: String) {
    return formatStyle + text + resetEscapeSequence
  }

  def reportDiagnosticWithColorAndContext(diagnostic: Diagnostic, host: CompilerHost): Unit {
    var output = ""

    if (diagnostic.file) {
      val { start, length, file } = diagnostic
      val { line: firstLine, character: firstLineChar } = getLineAndCharacterOfPosition(file, start)
      val { line: lastLine, character: lastLineChar } = getLineAndCharacterOfPosition(file, start + length)
      val lastLineInFile = getLineAndCharacterOfPosition(file, file.text.length).line
      val relativeFileName = getRelativeFileName(file.fileName, host)

      val hasMoreThanFiveLines = (lastLine - firstLine) >= 4
      var gutterWidth = (lastLine + 1 + "").length
      if (hasMoreThanFiveLines) {
        gutterWidth = Math.max(ellipsis.length, gutterWidth)
      }

      output += sys.newLine
      for (var i = firstLine; i <= lastLine; i++) {
        // If the error spans over 5 lines, we'll only show the first 2 and last 2 lines,
        // so we'll skip ahead to the second-to-last line.
        if (hasMoreThanFiveLines && firstLine + 1 < i && i < lastLine - 1) {
          output += formatAndReset(padLeft(ellipsis, gutterWidth), gutterStyleSequence) + gutterSeparator + sys.newLine
          i = lastLine - 1
        }

        val lineStart = getPositionOfLineAndCharacter(file, i, 0)
        val lineEnd = i < lastLineInFile ? getPositionOfLineAndCharacter(file, i + 1, 0) : file.text.length
        var lineContent = file.text.slice(lineStart, lineEnd)
        lineContent = lineContent.replace(/\s+$/g, "");  // trim from end
        lineContent = lineContent.replace("\t", " ");  // convert tabs to single spaces

        // Output the gutter and the actual contents of the line.
        output += formatAndReset(padLeft(i + 1 + "", gutterWidth), gutterStyleSequence) + gutterSeparator
        output += lineContent + sys.newLine

        // Output the gutter and the error span for the line using tildes.
        output += formatAndReset(padLeft("", gutterWidth), gutterStyleSequence) + gutterSeparator
        output += redForegroundEscapeSequence
        if (i == firstLine) {
          // If we're on the last line, then limit it to the last character of the last line.
          // Otherwise, we'll just squiggle the rest of the line, giving 'slice' no end position.
          val lastCharForLine = i == lastLine ? lastLineChar : ()

          output += lineContent.slice(0, firstLineChar).replace(/\S/g, " ")
          output += lineContent.slice(firstLineChar, lastCharForLine).replace(/./g, "~")
        }
        else if (i == lastLine) {
          output += lineContent.slice(0, lastLineChar).replace(/./g, "~")
        }
        else {
          // Squiggle the entire line.
          output += lineContent.replace(/./g, "~")
        }
        output += resetEscapeSequence

        output += sys.newLine
      }

      output += sys.newLine
      output += `${ relativeFileName }(${ firstLine + 1 },${ firstLineChar + 1 }): `
    }

    val categoryColor = categoryFormatMap[diagnostic.category]
    val category = DiagnosticCategory[diagnostic.category].toLowerCase()
    output += `${ formatAndReset(category, categoryColor) } TS${ diagnostic.code }: ${ flattenDiagnosticMessageText(diagnostic.messageText, sys.newLine) }`
    output += sys.newLine + sys.newLine

    sys.write(output)
  }

  def reportWatchDiagnostic(diagnostic: Diagnostic) {
    var output = new Date().toLocaleTimeString() + " - "

    if (diagnostic.file) {
      val loc = getLineAndCharacterOfPosition(diagnostic.file, diagnostic.start)
      output += `${ diagnostic.file.fileName }(${ loc.line + 1 },${ loc.character + 1 }): `
    }

    output += `${ flattenDiagnosticMessageText(diagnostic.messageText, sys.newLine) }${ sys.newLine }`

    sys.write(output)
  }

  def padLeft(s: String, length: Int) {
    while (s.length < length) {
      s = " " + s
    }
    return s
  }

  def padRight(s: String, length: Int) {
    while (s.length < length) {
      s = s + " "
    }

    return s
  }

  def reportStatisticalValue(name: String, value: String) {
    sys.write(padRight(name + ":", 12) + padLeft(value.toString(), 10) + sys.newLine)
  }

  def reportCountStatistic(name: String, count: Int) {
    reportStatisticalValue(name, "" + count)
  }

  def reportTimeStatistic(name: String, time: Int) {
    reportStatisticalValue(name, (time / 1000).toFixed(2) + "s")
  }

  def isJSONSupported() {
    return typeof JSON == "object" && typeof JSON.parse == "def"
  }

  def executeCommandLine(args: String[]): Unit {
    val commandLine = parseCommandLine(args)
    var configFileName: String;                 // Configuration file name (if any)
    var cachedConfigFileText: String;               // Cached configuration file text, used for reparsing (if any)
    var configFileWatcher: FileWatcher;             // Configuration file watcher
    var directoryWatcher: FileWatcher;              // Directory watcher to monitor source file addition/removal
    var cachedProgram: Program;                 // Program cached from last compilation
    var rootFileNames: String[];                // Root fileNames for compilation
    var compilerOptions: CompilerOptions;             // Compiler options for compilation
    var compilerHost: CompilerHost;               // Compiler host
    var hostGetSourceFile: typeof compilerHost.getSourceFile;   // getSourceFile method from default host
    var timerHandleForRecompilation: Int;          // Handle for 0.25s wait timer to trigger recompilation
    var timerHandleForDirectoryChanges: Int;         // Handle for 0.25s wait timer to trigger directory change handler

    // This map stores and reuses results of fileExists check that happen inside 'createProgram'
    // This allows to save time in module resolution heavy scenarios when existence of the same file might be checked multiple times.
    var cachedExistingFiles: Map<Boolean>
    var hostFileExists: typeof compilerHost.fileExists

    if (commandLine.options.locale) {
      if (!isJSONSupported()) {
        reportDiagnostic(createCompilerDiagnostic(Diagnostics.The_current_host_does_not_support_the_0_option, "--locale"), /* compilerHost */ ())
        return sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
      }
      validateLocaleAndSetLanguage(commandLine.options.locale, commandLine.errors)
    }

    // If there are any errors due to command line parsing and/or
    // setting up localization, report them and quit.
    if (commandLine.errors.length > 0) {
      reportDiagnostics(commandLine.errors, compilerHost)
      return sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
    }

    if (commandLine.options.init) {
      writeConfigFile(commandLine.options, commandLine.fileNames)
      return sys.exit(ExitStatus.Success)
    }

    if (commandLine.options.version) {
      printVersion()
      return sys.exit(ExitStatus.Success)
    }

    if (commandLine.options.help) {
      printVersion()
      printHelp()
      return sys.exit(ExitStatus.Success)
    }

    if (commandLine.options.project) {
      if (!isJSONSupported()) {
        reportDiagnostic(createCompilerDiagnostic(Diagnostics.The_current_host_does_not_support_the_0_option, "--project"), /* compilerHost */ ())
        return sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
      }
      if (commandLine.fileNames.length != 0) {
        reportDiagnostic(createCompilerDiagnostic(Diagnostics.Option_project_cannot_be_mixed_with_source_files_on_a_command_line), /* compilerHost */ ())
        return sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
      }

      val fileOrDirectory = normalizePath(commandLine.options.project)
      if (!fileOrDirectory /* current directory "." */ || sys.directoryExists(fileOrDirectory)) {
        configFileName = combinePaths(fileOrDirectory, "tsconfig.json")
        if (!sys.fileExists(configFileName)) {
          reportDiagnostic(createCompilerDiagnostic(Diagnostics.Cannot_find_a_tsconfig_json_file_at_the_specified_directory_Colon_0, commandLine.options.project), /* compilerHost */ ())
          return sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
        }
      }
      else {
        configFileName = fileOrDirectory
        if (!sys.fileExists(configFileName)) {
          reportDiagnostic(createCompilerDiagnostic(Diagnostics.The_specified_path_does_not_exist_Colon_0, commandLine.options.project), /* compilerHost */ ())
          return sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
        }
      }
    }
    else if (commandLine.fileNames.length == 0 && isJSONSupported()) {
      val searchPath = normalizePath(sys.getCurrentDirectory())
      configFileName = findConfigFile(searchPath, sys.fileExists)
    }

    if (commandLine.fileNames.length == 0 && !configFileName) {
      printVersion()
      printHelp()
      return sys.exit(ExitStatus.Success)
    }

    // Firefox has Object.prototype.watch
    if (commandLine.options.watch && commandLine.options.hasOwnProperty("watch")) {
      if (!sys.watchFile) {
        reportDiagnostic(createCompilerDiagnostic(Diagnostics.The_current_host_does_not_support_the_0_option, "--watch"), /* compilerHost */ ())
        return sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
      }
      if (configFileName) {
        val configFilePath = toPath(configFileName, sys.getCurrentDirectory(), createGetCanonicalFileName(sys.useCaseSensitiveFileNames))
        configFileWatcher = sys.watchFile(configFilePath, configFileChanged)
      }
      if (sys.watchDirectory && configFileName) {
        val directory = ts.getDirectoryPath(configFileName)
        directoryWatcher = sys.watchDirectory(
          // When the configFileName is just "tsconfig.json", the watched directory should be
          // the current directory; if there is a given "project" parameter, then the configFileName
          // is an absolute file name.
          directory == "" ? "." : directory,
          watchedDirectoryChanged, /*recursive*/ true)
      }
    }

    performCompilation()

    def parseConfigFile(): ParsedCommandLine {
      if (!cachedConfigFileText) {
        try {
          cachedConfigFileText = sys.readFile(configFileName)
        }
        catch (e) {
          val error = createCompilerDiagnostic(Diagnostics.Cannot_read_file_0_Colon_1, configFileName, e.message)
          reportWatchDiagnostic(error)
          sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
          return
        }
      }
      if (!cachedConfigFileText) {
        val error = createCompilerDiagnostic(Diagnostics.File_0_not_found, configFileName)
        reportDiagnostics([error], /* compilerHost */ ())
        sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
        return
      }

      val result = parseConfigFileTextToJson(configFileName, cachedConfigFileText)
      val configObject = result.config
      if (!configObject) {
        reportDiagnostics([result.error], /* compilerHost */ ())
        sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
        return
      }
      val configParseResult = parseJsonConfigFileContent(configObject, sys, getNormalizedAbsolutePath(getDirectoryPath(configFileName), sys.getCurrentDirectory()), commandLine.options, configFileName)
      if (configParseResult.errors.length > 0) {
        reportDiagnostics(configParseResult.errors, /* compilerHost */ ())
        sys.exit(ExitStatus.DiagnosticsPresent_OutputsSkipped)
        return
      }
      return configParseResult
    }

    // Invoked to perform initial compilation or re-compilation in watch mode
    def performCompilation() {

      if (!cachedProgram) {
        if (configFileName) {
          val configParseResult = parseConfigFile()
          rootFileNames = configParseResult.fileNames
          compilerOptions = configParseResult.options
        }
        else {
          rootFileNames = commandLine.fileNames
          compilerOptions = commandLine.options
        }
        compilerHost = createCompilerHost(compilerOptions)
        hostGetSourceFile = compilerHost.getSourceFile
        compilerHost.getSourceFile = getSourceFile

        hostFileExists = compilerHost.fileExists
        compilerHost.fileExists = cachedFileExists
      }

      if (compilerOptions.pretty) {
        reportDiagnostic = reportDiagnosticWithColorAndContext
      }

      // reset the cache of existing files
      cachedExistingFiles = {}

      val compileResult = compile(rootFileNames, compilerOptions, compilerHost)

      if (!compilerOptions.watch) {
        return sys.exit(compileResult.exitStatus)
      }

      setCachedProgram(compileResult.program)
      reportWatchDiagnostic(createCompilerDiagnostic(Diagnostics.Compilation_complete_Watching_for_file_changes))
    }

    def cachedFileExists(fileName: String): Boolean {
      if (hasProperty(cachedExistingFiles, fileName)) {
        return cachedExistingFiles[fileName]
      }
      return cachedExistingFiles[fileName] = hostFileExists(fileName)
    }

    def getSourceFile(fileName: String, languageVersion: ScriptTarget, onError?: (message: String) => Unit) {
      // Return existing SourceFile object if one is available
      if (cachedProgram) {
        val sourceFile = cachedProgram.getSourceFile(fileName)
        // A modified source file has no watcher and should not be reused
        if (sourceFile && sourceFile.fileWatcher) {
          return sourceFile
        }
      }
      // Use default host def
      val sourceFile = hostGetSourceFile(fileName, languageVersion, onError)
      if (sourceFile && compilerOptions.watch) {
        // Attach a file watcher
        val filePath = toPath(sourceFile.fileName, sys.getCurrentDirectory(), createGetCanonicalFileName(sys.useCaseSensitiveFileNames))
        sourceFile.fileWatcher = sys.watchFile(filePath, (fileName: String, removed?: Boolean) => sourceFileChanged(sourceFile, removed))
      }
      return sourceFile
    }

    // Change cached program to the given program
    def setCachedProgram(program: Program) {
      if (cachedProgram) {
        val newSourceFiles = program ? program.getSourceFiles() : ()
        forEach(cachedProgram.getSourceFiles(), sourceFile => {
          if (!(newSourceFiles && contains(newSourceFiles, sourceFile))) {
            if (sourceFile.fileWatcher) {
              sourceFile.fileWatcher.close()
              sourceFile.fileWatcher = ()
            }
          }
        })
      }
      cachedProgram = program
    }

    // If a source file changes, mark it as unwatched and start the recompilation timer
    def sourceFileChanged(sourceFile: SourceFile, removed?: Boolean) {
      sourceFile.fileWatcher.close()
      sourceFile.fileWatcher = ()
      if (removed) {
        val index = rootFileNames.indexOf(sourceFile.fileName)
        if (index >= 0) {
          rootFileNames.splice(index, 1)
        }
      }
      startTimerForRecompilation()
    }

    // If the configuration file changes, forget cached program and start the recompilation timer
    def configFileChanged() {
      setCachedProgram(())
      cachedConfigFileText = ()
      startTimerForRecompilation()
    }

    def watchedDirectoryChanged(fileName: String) {
      if (fileName && !ts.isSupportedSourceFileName(fileName, compilerOptions)) {
        return
      }

      startTimerForHandlingDirectoryChanges()
    }

    def startTimerForHandlingDirectoryChanges() {
      if (timerHandleForDirectoryChanges) {
        clearTimeout(timerHandleForDirectoryChanges)
      }
      timerHandleForDirectoryChanges = setTimeout(directoryChangeHandler, 250)
    }

    def directoryChangeHandler() {
      val parsedCommandLine = parseConfigFile()
      val newFileNames = ts.map(parsedCommandLine.fileNames, compilerHost.getCanonicalFileName)
      val canonicalRootFileNames = ts.map(rootFileNames, compilerHost.getCanonicalFileName)

      // We check if the project file list has changed. If so, we just throw away the old program and start fresh.
      if (!arrayIsEqualTo(newFileNames && newFileNames.sort(), canonicalRootFileNames && canonicalRootFileNames.sort())) {
        setCachedProgram(())
        startTimerForRecompilation()
      }
    }

    // Upon detecting a file change, wait for 250ms and then perform a recompilation. This gives batch
    // operations (such as saving all modified files in an editor) a chance to complete before we kick
    // off a new compilation.
    def startTimerForRecompilation() {
      if (timerHandleForRecompilation) {
        clearTimeout(timerHandleForRecompilation)
      }
      timerHandleForRecompilation = setTimeout(recompile, 250)
    }

    def recompile() {
      timerHandleForRecompilation = ()
      reportWatchDiagnostic(createCompilerDiagnostic(Diagnostics.File_change_detected_Starting_incremental_compilation))
      performCompilation()
    }
  }

  def compile(fileNames: String[], compilerOptions: CompilerOptions, compilerHost: CompilerHost) {
    ioReadTime = 0
    ioWriteTime = 0
    programTime = 0
    bindTime = 0
    checkTime = 0
    emitTime = 0

    val program = createProgram(fileNames, compilerOptions, compilerHost)
    val exitStatus = compileProgram()

    if (compilerOptions.listFiles) {
      forEach(program.getSourceFiles(), file => {
        sys.write(file.fileName + sys.newLine)
      })
    }

    if (compilerOptions.diagnostics) {
      val memoryUsed = sys.getMemoryUsage ? sys.getMemoryUsage() : -1
      reportCountStatistic("Files", program.getSourceFiles().length)
      reportCountStatistic("Lines", countLines(program))
      reportCountStatistic("Nodes", program.getNodeCount())
      reportCountStatistic("Identifiers", program.getIdentifierCount())
      reportCountStatistic("Symbols", program.getSymbolCount())
      reportCountStatistic("Types", program.getTypeCount())

      if (memoryUsed >= 0) {
        reportStatisticalValue("Memory used", Math.round(memoryUsed / 1000) + "K")
      }

      // Individual component times.
      // Note: To match the behavior of previous versions of the compiler, the reported parse time includes
      // I/O read time and processing time for triple-slash references and module imports, and the reported
      // emit time includes I/O write time. We preserve this behavior so we can accurately compare times.
      reportTimeStatistic("I/O read", ioReadTime)
      reportTimeStatistic("I/O write", ioWriteTime)
      reportTimeStatistic("Parse time", programTime)
      reportTimeStatistic("Bind time", bindTime)
      reportTimeStatistic("Check time", checkTime)
      reportTimeStatistic("Emit time", emitTime)
      reportTimeStatistic("Total time", programTime + bindTime + checkTime + emitTime)
    }

    return { program, exitStatus }

    def compileProgram(): ExitStatus {
      var diagnostics: Diagnostic[]

      // First get and report any syntactic errors.
      diagnostics = program.getSyntacticDiagnostics()

      // If we didn't have any syntactic errors, then also try getting the global and
      // semantic errors.
      if (diagnostics.length == 0) {
        diagnostics = program.getOptionsDiagnostics().concat(program.getGlobalDiagnostics())

        if (diagnostics.length == 0) {
          diagnostics = program.getSemanticDiagnostics()
        }
      }

      reportDiagnostics(diagnostics, compilerHost)

      // If the user doesn't want us to emit, then we're done at this point.
      if (compilerOptions.noEmit) {
        return diagnostics.length
          ? ExitStatus.DiagnosticsPresent_OutputsSkipped
          : ExitStatus.Success
      }

      // Otherwise, emit and report any errors we ran into.
      val emitOutput = program.emit()
      reportDiagnostics(emitOutput.diagnostics, compilerHost)

      // If the emitter didn't emit anything, then pass that value along.
      if (emitOutput.emitSkipped) {
        return ExitStatus.DiagnosticsPresent_OutputsSkipped
      }

      // The emitter emitted something, inform the caller if that happened in the presence
      // of diagnostics or not.
      if (diagnostics.length > 0 || emitOutput.diagnostics.length > 0) {
        return ExitStatus.DiagnosticsPresent_OutputsGenerated
      }

      return ExitStatus.Success
    }
  }

  def printVersion() {
    sys.write(getDiagnosticText(Diagnostics.Version_0, ts.version) + sys.newLine)
  }

  def printHelp() {
    var output = ""

    // We want to align our "syntax" and "examples" commands to a certain margin.
    val syntaxLength = getDiagnosticText(Diagnostics.Syntax_Colon_0, "").length
    val examplesLength = getDiagnosticText(Diagnostics.Examples_Colon_0, "").length
    var marginLength = Math.max(syntaxLength, examplesLength)

    // Build up the syntactic skeleton.
    var syntax = makePadding(marginLength - syntaxLength)
    syntax += "tsc [" + getDiagnosticText(Diagnostics.options) + "] [" + getDiagnosticText(Diagnostics.file) + " ...]"

    output += getDiagnosticText(Diagnostics.Syntax_Colon_0, syntax)
    output += sys.newLine + sys.newLine

    // Build up the list of examples.
    val padding = makePadding(marginLength)
    output += getDiagnosticText(Diagnostics.Examples_Colon_0, makePadding(marginLength - examplesLength) + "tsc hello.ts") + sys.newLine
    output += padding + "tsc --out file.js file.ts" + sys.newLine
    output += padding + "tsc @args.txt" + sys.newLine
    output += sys.newLine

    output += getDiagnosticText(Diagnostics.Options_Colon) + sys.newLine

    // Sort our options by their names, (e.g. "--noImplicitAny" comes before "--watch")
    val optsList = filter(optionDeclarations.slice(), v => !v.experimental)
    optsList.sort((a, b) => compareValues<String>(a.name.toLowerCase(), b.name.toLowerCase()))

    // We want our descriptions to align at the same column in our output,
    // so we keep track of the longest option usage String.
    marginLength = 0
    val usageColumn: String[] = []; // Things like "-d, --declaration" go in here.
    val descriptionColumn: String[] = []

    for (var i = 0; i < optsList.length; i++) {
      val option = optsList[i]

      // If an option lacks a description,
      // it is not officially supported.
      if (!option.description) {
        continue
      }

      var usageText = " "
      if (option.shortName) {
        usageText += "-" + option.shortName
        usageText += getParamType(option)
        usageText += ", "
      }

      usageText += "--" + option.name
      usageText += getParamType(option)

      usageColumn.push(usageText)
      descriptionColumn.push(getDiagnosticText(option.description))

      // Set the new margin for the description column if necessary.
      marginLength = Math.max(usageText.length, marginLength)
    }

    // Special case that can't fit in the loop.
    val usageText = " @<" + getDiagnosticText(Diagnostics.file) + ">"
    usageColumn.push(usageText)
    descriptionColumn.push(getDiagnosticText(Diagnostics.Insert_command_line_options_and_files_from_a_file))
    marginLength = Math.max(usageText.length, marginLength)

    // Print out each row, aligning all the descriptions on the same column.
    for (var i = 0; i < usageColumn.length; i++) {
      val usage = usageColumn[i]
      val description = descriptionColumn[i]
      output += usage + makePadding(marginLength - usage.length + 2) + description + sys.newLine
    }

    sys.write(output)
    return

    def getParamType(option: CommandLineOption) {
      if (option.paramType != ()) {
        return " " + getDiagnosticText(option.paramType)
      }
      return ""
    }

    def makePadding(paddingLength: Int): String {
      return Array(paddingLength + 1).join(" ")
    }
  }

  def writeConfigFile(options: CompilerOptions, fileNames: String[]) {
    val currentDirectory = sys.getCurrentDirectory()
    val file = normalizePath(combinePaths(currentDirectory, "tsconfig.json"))
    if (sys.fileExists(file)) {
      reportDiagnostic(createCompilerDiagnostic(Diagnostics.A_tsconfig_json_file_is_already_defined_at_Colon_0, file), /* compilerHost */ ())
    }
    else {
      val compilerOptions = extend(options, defaultInitCompilerOptions)
      val configurations: any = {
        compilerOptions: serializeCompilerOptions(compilerOptions),
        exclude: ["node_modules"]
      }

      if (fileNames && fileNames.length) {
        // only set the files property if we have at least one file
        configurations.files = fileNames
      }

      sys.writeFile(file, JSON.stringify(configurations, (), 4))
      reportDiagnostic(createCompilerDiagnostic(Diagnostics.Successfully_created_a_tsconfig_json_file), /* compilerHost */ ())
    }

    return

    def serializeCompilerOptions(options: CompilerOptions): Map<String | Int | Boolean> {
      val result: Map<String | Int | Boolean> = {}
      val optionsNameMap = getOptionNameMap().optionNameMap

      for (val name in options) {
        if (hasProperty(options, name)) {
          // tsconfig only options cannot be specified via command line,
          // so we can assume that only types that can appear here String | Int | Boolean
          val value = <String | Int | Boolean>options[name]
          switch (name) {
            case "init":
            case "watch":
            case "version":
            case "help":
            case "project":
              break
            default:
              var optionDefinition = optionsNameMap[name.toLowerCase()]
              if (optionDefinition) {
                if (typeof optionDefinition.type == "String") {
                  // String, Int or Boolean
                  result[name] = value
                }
                else {
                  // Enum
                  val typeMap = <Map<Int>>optionDefinition.type
                  for (val key in typeMap) {
                    if (hasProperty(typeMap, key)) {
                      if (typeMap[key] == value)
                        result[name] = key
                    }
                  }
                }
              }
              break
          }
        }
      }
      return result
    }
  }
}

ts.executeCommandLine(ts.sys.args)
