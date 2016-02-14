package be.doeraene.tsc

/// <reference path="sys.ts" />
/// <reference path="emitter.ts" />
/// <reference path="core.ts" />

object Program {
  /* @internal */ var programTime = 0
  /* @internal */ var emitTime = 0
  /* @internal */ var ioReadTime = 0
  /* @internal */ var ioWriteTime = 0

  /** The version of the TypeScript compiler release */

  val emptyArray: any[] = []

  val version = "1.9.0"

  def findConfigFile(searchPath: String, fileExists: (fileName: String) => Boolean): String = {
    var fileName = "tsconfig.json"
    while (true) {
      if (fileExists(fileName)) {
        return fileName
      }
      val parentPath = getDirectoryPath(searchPath)
      if (parentPath == searchPath) {
        break
      }
      searchPath = parentPath
      fileName = "../" + fileName
    }
    return ()
  }

  def resolveTripleslashReference(moduleName: String, containingFile: String): String = {
    val basePath = getDirectoryPath(containingFile)
    val referencedFileName = isRootedDiskPath(moduleName) ? moduleName : combinePaths(basePath, moduleName)
    return normalizePath(referencedFileName)
  }

  def trace(host: ModuleResolutionHost, message: DiagnosticMessage, ...args: any[]): Unit
  def trace(host: ModuleResolutionHost, message: DiagnosticMessage): Unit = {
    host.trace(formatMessage.apply((), arguments))
  }

  def isTraceEnabled(compilerOptions: CompilerOptions, host: ModuleResolutionHost): Boolean = {
    return compilerOptions.traceModuleResolution && host.trace != ()
  }

  def startsWith(str: String, prefix: String): Boolean = {
    return str.lastIndexOf(prefix, 0) == 0
  }

  def endsWith(str: String, suffix: String): Boolean = {
    val expectedPos = str.length - suffix.length
    return str.indexOf(suffix, expectedPos) == expectedPos
  }

  def hasZeroOrOneAsteriskCharacter(str: String): Boolean = {
    var seenAsterisk = false
    for (var i = 0; i < str.length; i++) {
      if (str.charCodeAt(i) == CharacterCodes.asterisk) {
        if (!seenAsterisk) {
          seenAsterisk = true
        }
        else {
          // have already seen asterisk
          return false
        }
      }
    }
    return true
  }

  def createResolvedModule(resolvedFileName: String, isExternalLibraryImport: Boolean, failedLookupLocations: String[]): ResolvedModuleWithFailedLookupLocations = {
    return { resolvedModule: resolvedFileName ? { resolvedFileName, isExternalLibraryImport } : (), failedLookupLocations }
  }

  def moduleHasNonRelativeName(moduleName: String): Boolean = {
    if (isRootedDiskPath(moduleName)) {
      return false
    }

    val i = moduleName.lastIndexOf("./", 1)
    val startsWithDotSlashOrDotDotSlash = i == 0 || (i == 1 && moduleName.charCodeAt(0) == CharacterCodes.dot)
    return !startsWithDotSlashOrDotDotSlash
  }

  trait ModuleResolutionState {
    host: ModuleResolutionHost
    compilerOptions: CompilerOptions
    traceEnabled: Boolean
    // skip .tsx files if jsx is not enabled
    skipTsx: Boolean
  }

  def resolveModuleName(moduleName: String, containingFile: String, compilerOptions: CompilerOptions, host: ModuleResolutionHost): ResolvedModuleWithFailedLookupLocations = {
    val traceEnabled = isTraceEnabled(compilerOptions, host)
    if (traceEnabled) {
      trace(host, Diagnostics.Resolving_module_0_from_1, moduleName, containingFile)
    }

    var moduleResolution = compilerOptions.moduleResolution
    if (moduleResolution == ()) {
      moduleResolution = getEmitModuleKind(compilerOptions) == ModuleKind.CommonJS ? ModuleResolutionKind.NodeJs : ModuleResolutionKind.Classic
      if (traceEnabled) {
        trace(host, Diagnostics.Module_resolution_kind_is_not_specified_using_0, ModuleResolutionKind[moduleResolution])
      }
    }
    else {
      if (traceEnabled) {
        trace(host, Diagnostics.Explicitly_specified_module_resolution_kind_Colon_0, ModuleResolutionKind[moduleResolution])
      }
    }

    var result: ResolvedModuleWithFailedLookupLocations
    switch (moduleResolution) {
      case ModuleResolutionKind.NodeJs:
        result = nodeModuleNameResolver(moduleName, containingFile, compilerOptions, host)
        break
      case ModuleResolutionKind.Classic:
        result = classicNameResolver(moduleName, containingFile, compilerOptions, host)
        break
    }

    if (traceEnabled) {
      if (result.resolvedModule) {
        trace(host, Diagnostics.Module_name_0_was_successfully_resolved_to_1, moduleName, result.resolvedModule.resolvedFileName)
      }
      else {
        trace(host, Diagnostics.Module_name_0_was_not_resolved, moduleName)
      }
    }

    return result
  }

  /*
   * Every module resolution kind can has its specific understanding how to load module from a specific path on disk
   * I.e. for path '/a/b/c':
   * - Node loader will first to try to check if '/a/b/c' points to a file with some supported extension and if this fails
   * it will try to load module from directory: directory '/a/b/c' should exist and it should have either 'package.json' with
   * 'typings' entry or file 'index' with some supported extension
   * - Classic loader will only try to interpret '/a/b/c' as file.
   */
  type ResolutionKindSpecificLoader = (candidate: String, extensions: String[], failedLookupLocations: String[], onlyRecordFailures: Boolean, state: ModuleResolutionState) => String

  /**
   * Any module resolution kind can be augmented with optional settings: 'baseUrl', 'paths' and 'rootDirs' - they are used to
   * mitigate differences between design time structure of the project and its runtime counterpart so the same import name
   * can be resolved successfully by TypeScript compiler and runtime module loader.
   * If these settings are set then loading procedure will try to use them to resolve module name and it can of failure it will
   * fallback to standard resolution routine.
   *
   * - baseUrl - this setting controls how non-relative module names are resolved. If this setting is specified then non-relative
   * names will be resolved relative to baseUrl: i.e. if baseUrl is '/a/b' then candidate location to resolve module name 'c/d' will
   * be '/a/b/c/d'
   * - paths - this setting can only be used when baseUrl is specified. allows to tune how non-relative module names
   * will be resolved based on the content of the module name.
   * Structure of 'paths' compiler options
   * 'paths': {
   *  pattern-1: [...substitutions],
   *  pattern-2: [...substitutions],
   *  ...
   *  pattern-n: [...substitutions]
   * }
   * Pattern here is a String that can contain zero or one '*' character. During module resolution module name will be matched against
   * all patterns in the list. Matching for patterns that don't contain '*' means that module name must be equal to pattern respecting the case.
   * If pattern contains '*' then to match pattern "<prefix>*<suffix>" module name must start with the <prefix> and end with <suffix>.
   * <MatchedStar> denotes part of the module name between <prefix> and <suffix>.
   * If module name can be matches with multiple patterns then pattern with the longest prefix will be picked.
   * After selecting pattern we'll use list of substitutions to get candidate locations of the module and the try to load module
   * from the candidate location.
   * Substitution is a String that can contain zero or one '*'. To get candidate location from substitution we'll pick every
   * substitution in the list and replace '*' with <MatchedStar> String. If candidate location is not rooted it
   * will be converted to absolute using baseUrl.
   * For example:
   * baseUrl: /a/b/c
   * "paths": {
   *   // match all module names
   *   "*": [
   *     "*",    // use matched name as is,
   *           // <matched name> will be looked as /a/b/c/<matched name>
   *
   *     "folder1/*" // substitution will convert matched name to 'folder1/<matched name>',
   *           // since it is not rooted then final candidate location will be /a/b/c/folder1/<matched name>
   *   ],
   *   // match module names that start with 'components/'
   *   "components/*": [ "/root/components/*" ] // substitution will convert /components/folder1/<matched name> to '/root/components/folder1/<matched name>',
   *                        // it is rooted so it will be final candidate location
   * }
   *
   * 'rootDirs' allows the project to be spreaded across multiple locations and resolve modules with relative names as if
   * they were in the same location. For example lets say there are two files
   * '/local/src/content/file1.ts'
   * '/shared/components/contracts/src/content/protocols/file2.ts'
   * After bundling content of '/shared/components/contracts/src' will be merged with '/local/src' so
   * if file1 has the following import 'import {x} from "./protocols/file2"' it will be resolved successfully in runtime.
   * 'rootDirs' provides the way to tell compiler that in order to get the whole project it should behave as if content of all
   * root dirs were merged together.
   * I.e. for the example above 'rootDirs' will have two entries: [ '/local/src', '/shared/components/contracts/src' ].
   * Compiler will first convert './protocols/file2' into absolute path relative to the location of containing file:
   * '/local/src/content/protocols/file2' and try to load it - failure.
   * Then it will search 'rootDirs' looking for a longest matching prefix of this absolute path and if such prefix is found - absolute path will
   * be converted to a path relative to found rootDir entry './content/protocols/file2' (*). As a last step compiler will check all remaining
   * entries in 'rootDirs', use them to build absolute path out of (*) and try to resolve module from this location.
   */
  def tryLoadModuleUsingOptionalResolutionSettings(moduleName: String, containingDirectory: String, loader: ResolutionKindSpecificLoader,
    failedLookupLocations: String[], supportedExtensions: String[], state: ModuleResolutionState): String {

    if (moduleHasNonRelativeName(moduleName)) {
      return tryLoadModuleUsingBaseUrl(moduleName, loader, failedLookupLocations, supportedExtensions, state)
    }
    else {
      return tryLoadModuleUsingRootDirs(moduleName, containingDirectory, loader, failedLookupLocations, supportedExtensions, state)
    }
  }

  def tryLoadModuleUsingRootDirs(moduleName: String, containingDirectory: String, loader: ResolutionKindSpecificLoader,
    failedLookupLocations: String[], supportedExtensions: String[], state: ModuleResolutionState): String {

    if (!state.compilerOptions.rootDirs) {
      return ()
    }

    if (state.traceEnabled) {
      trace(state.host, Diagnostics.rootDirs_option_is_set_using_it_to_resolve_relative_module_name_0, moduleName)
    }

    val candidate = normalizePath(combinePaths(containingDirectory, moduleName))

    var matchedRootDir: String
    var matchedNormalizedPrefix: String
    for (val rootDir of state.compilerOptions.rootDirs) {
      // rootDirs are expected to be absolute
      // in case of tsconfig.json this will happen automatically - compiler will expand relative names
      // using location of tsconfig.json as base location
      var normalizedRoot = normalizePath(rootDir)
      if (!endsWith(normalizedRoot, directorySeparator)) {
        normalizedRoot += directorySeparator
      }
      val isLongestMatchingPrefix =
        startsWith(candidate, normalizedRoot) &&
        (matchedNormalizedPrefix == () || matchedNormalizedPrefix.length < normalizedRoot.length)

      if (state.traceEnabled) {
        trace(state.host, Diagnostics.Checking_if_0_is_the_longest_matching_prefix_for_1_2, normalizedRoot, candidate, isLongestMatchingPrefix)
      }

      if (isLongestMatchingPrefix) {
        matchedNormalizedPrefix = normalizedRoot
        matchedRootDir = rootDir
      }
    }
    if (matchedNormalizedPrefix) {
      if (state.traceEnabled) {
        trace(state.host, Diagnostics.Longest_matching_prefix_for_0_is_1, candidate, matchedNormalizedPrefix)
      }
      val suffix = candidate.substr(matchedNormalizedPrefix.length)

      // first - try to load from a initial location
      if (state.traceEnabled) {
        trace(state.host, Diagnostics.Loading_0_from_the_root_dir_1_candidate_location_2, suffix, matchedNormalizedPrefix, candidate)
      }
      val resolvedFileName = loader(candidate, supportedExtensions, failedLookupLocations, !directoryProbablyExists(containingDirectory, state.host), state)
      if (resolvedFileName) {
        return resolvedFileName
      }

      if (state.traceEnabled) {
        trace(state.host, Diagnostics.Trying_other_entries_in_rootDirs)
      }
      // then try to resolve using remaining entries in rootDirs
      for (val rootDir of state.compilerOptions.rootDirs) {
        if (rootDir == matchedRootDir) {
          // skip the initially matched entry
          continue
        }
        val candidate = combinePaths(normalizePath(rootDir), suffix)
        if (state.traceEnabled) {
          trace(state.host, Diagnostics.Loading_0_from_the_root_dir_1_candidate_location_2, suffix, rootDir, candidate)
        }
        val baseDirectory = getDirectoryPath(candidate)
        val resolvedFileName = loader(candidate, supportedExtensions, failedLookupLocations, !directoryProbablyExists(baseDirectory, state.host), state)
        if (resolvedFileName) {
          return resolvedFileName
        }
      }
      if (state.traceEnabled) {
        trace(state.host, Diagnostics.Module_resolution_using_rootDirs_has_failed)
      }
    }
    return ()
  }

  def tryLoadModuleUsingBaseUrl(moduleName: String, loader: ResolutionKindSpecificLoader, failedLookupLocations: String[],
    supportedExtensions: String[], state: ModuleResolutionState): String {

    if (!state.compilerOptions.baseUrl) {
      return ()
    }
    if (state.traceEnabled) {
      trace(state.host, Diagnostics.baseUrl_option_is_set_to_0_using_this_value_to_resolve_non_relative_module_name_1, state.compilerOptions.baseUrl, moduleName)
    }

    var longestMatchPrefixLength = -1
    var matchedPattern: String
    var matchedStar: String

    if (state.compilerOptions.paths) {
      if (state.traceEnabled) {
        trace(state.host, Diagnostics.paths_option_is_specified_looking_for_a_pattern_to_match_module_name_0, moduleName)
      }

      for (val key in state.compilerOptions.paths) {
        val pattern: String = key
        val indexOfStar = pattern.indexOf("*")
        if (indexOfStar != -1) {
          val prefix = pattern.substr(0, indexOfStar)
          val suffix = pattern.substr(indexOfStar + 1)
          if (moduleName.length >= prefix.length + suffix.length &&
            startsWith(moduleName, prefix) &&
            endsWith(moduleName, suffix)) {

            // use length of prefix as betterness criteria
            if (prefix.length > longestMatchPrefixLength) {
              longestMatchPrefixLength = prefix.length
              matchedPattern = pattern
              matchedStar = moduleName.substr(prefix.length, moduleName.length - suffix.length)
            }
          }
        }
        else if (pattern == moduleName) {
          // pattern was matched as is - no need to search further
          matchedPattern = pattern
          matchedStar = ()
          break
        }
      }
    }

    if (matchedPattern) {
      if (state.traceEnabled) {
        trace(state.host, Diagnostics.Module_name_0_matched_pattern_1, moduleName, matchedPattern)
      }
      for (val subst of state.compilerOptions.paths[matchedPattern]) {
        val path = matchedStar ? subst.replace("\*", matchedStar) : subst
        val candidate = normalizePath(combinePaths(state.compilerOptions.baseUrl, path))
        if (state.traceEnabled) {
          trace(state.host, Diagnostics.Trying_substitution_0_candidate_module_location_Colon_1, subst, path)
        }
        val resolvedFileName = loader(candidate, supportedExtensions, failedLookupLocations, !directoryProbablyExists(getDirectoryPath(candidate), state.host), state)
        if (resolvedFileName) {
          return resolvedFileName
        }
      }
      return ()
    }
    else {
      val candidate = normalizePath(combinePaths(state.compilerOptions.baseUrl, moduleName))

      if (state.traceEnabled) {
        trace(state.host, Diagnostics.Resolving_module_name_0_relative_to_base_url_1_2, moduleName, state.compilerOptions.baseUrl, candidate)
      }

      return loader(candidate, supportedExtensions, failedLookupLocations, !directoryProbablyExists(getDirectoryPath(candidate), state.host), state)
    }
  }

  def nodeModuleNameResolver(moduleName: String, containingFile: String, compilerOptions: CompilerOptions, host: ModuleResolutionHost): ResolvedModuleWithFailedLookupLocations = {
    val containingDirectory = getDirectoryPath(containingFile)
    val supportedExtensions = getSupportedExtensions(compilerOptions)
    val traceEnabled = isTraceEnabled(compilerOptions, host)

    val failedLookupLocations: String[] = []
    val state = {compilerOptions, host, traceEnabled, skipTsx: false}
    var resolvedFileName = tryLoadModuleUsingOptionalResolutionSettings(moduleName, containingDirectory, nodeLoadModuleByRelativeName,
      failedLookupLocations, supportedExtensions, state)

    if (resolvedFileName) {
      return createResolvedModule(resolvedFileName, /*isExternalLibraryImport*/false, failedLookupLocations)
    }

    var isExternalLibraryImport = false
    if (moduleHasNonRelativeName(moduleName)) {
      if (traceEnabled) {
        trace(host, Diagnostics.Loading_module_0_from_node_modules_folder, moduleName)
      }
      resolvedFileName = loadModuleFromNodeModules(moduleName, containingDirectory, failedLookupLocations, state)
      isExternalLibraryImport = resolvedFileName != ()
    }
    else {
      val candidate = normalizePath(combinePaths(containingDirectory, moduleName))
      resolvedFileName = nodeLoadModuleByRelativeName(candidate, supportedExtensions, failedLookupLocations, /*onlyRecordFailures*/ false, state)
    }
    return createResolvedModule(resolvedFileName, isExternalLibraryImport, failedLookupLocations)
  }

  def nodeLoadModuleByRelativeName(candidate: String, supportedExtensions: String[], failedLookupLocations: String[],
    onlyRecordFailures: Boolean, state: ModuleResolutionState): String {

    if (state.traceEnabled) {
      trace(state.host, Diagnostics.Loading_module_as_file_Slash_folder_candidate_module_location_0, candidate)
    }

    val resolvedFileName = loadModuleFromFile(candidate, supportedExtensions, failedLookupLocations, onlyRecordFailures, state)

    return resolvedFileName || loadNodeModuleFromDirectory(supportedExtensions, candidate, failedLookupLocations, onlyRecordFailures, state)
  }

  /* @internal */
  def directoryProbablyExists(directoryName: String, host: { directoryExists?: (directoryName: String) => Boolean } ): Boolean = {
    // if host does not support 'directoryExists' assume that directory will exist
    return !host.directoryExists || host.directoryExists(directoryName)
  }

  /**
   * @param {Boolean} onlyRecordFailures - if true then def won't try to actually load files but instead record all attempts as failures. This flag is necessary
   * in cases when we know upfront that all load attempts will fail (because containing folder does not exists) however we still need to record all failed lookup locations.
   */
  def loadModuleFromFile(candidate: String, extensions: String[], failedLookupLocation: String[], onlyRecordFailures: Boolean, state: ModuleResolutionState): String = {
    return forEach(extensions, tryLoad)

    def tryLoad(ext: String): String = {
      if (ext == ".tsx" && state.skipTsx) {
        return ()
      }
      val fileName = fileExtensionIs(candidate, ext) ? candidate : candidate + ext
      if (!onlyRecordFailures && state.host.fileExists(fileName)) {
        if (state.traceEnabled) {
          trace(state.host, Diagnostics.File_0_exist_use_it_as_a_module_resolution_result, fileName)
        }
        return fileName
      }
      else {
        if (state.traceEnabled) {
          trace(state.host, Diagnostics.File_0_does_not_exist, fileName)
        }
        failedLookupLocation.push(fileName)
        return ()
      }
    }
  }

  def loadNodeModuleFromDirectory(extensions: String[], candidate: String, failedLookupLocation: String[], onlyRecordFailures: Boolean, state: ModuleResolutionState): String = {
    val packageJsonPath = combinePaths(candidate, "package.json")
    val directoryExists = !onlyRecordFailures && directoryProbablyExists(candidate, state.host)
    if (directoryExists && state.host.fileExists(packageJsonPath)) {
      if (state.traceEnabled) {
        trace(state.host, Diagnostics.Found_package_json_at_0, packageJsonPath)
      }

      var jsonContent: { typings?: String }

      try {
        val jsonText = state.host.readFile(packageJsonPath)
        jsonContent = jsonText ? <{ typings?: String }>JSON.parse(jsonText) : { typings: () }
      }
      catch (e) {
        // gracefully handle if readFile fails or returns not JSON
        jsonContent = { typings: () }
      }

      if (jsonContent.typings) {
        if (typeof jsonContent.typings == "String") {
          val typingsFile = normalizePath(combinePaths(candidate, jsonContent.typings))
          if (state.traceEnabled) {
            trace(state.host, Diagnostics.package_json_has_typings_field_0_that_references_1, jsonContent.typings, typingsFile)
          }
          val result = loadModuleFromFile(typingsFile, extensions, failedLookupLocation, !directoryProbablyExists(getDirectoryPath(typingsFile), state.host), state)
          if (result) {
            return result
          }
        }
        else if (state.traceEnabled) {
          trace(state.host, Diagnostics.Expected_type_of_typings_field_in_package_json_to_be_string_got_0, typeof jsonContent.typings)
        }
      }
      else {
        if (state.traceEnabled) {
          trace(state.host, Diagnostics.package_json_does_not_have_typings_field)
        }
      }
    }
    else {
      if (state.traceEnabled) {
        trace(state.host, Diagnostics.File_0_does_not_exist, packageJsonPath)
      }
      // record package json as one of failed lookup locations - in the future if this file will appear it will invalidate resolution results
      failedLookupLocation.push(packageJsonPath)
    }

    return loadModuleFromFile(combinePaths(candidate, "index"), extensions, failedLookupLocation, !directoryExists, state)
  }

  def loadModuleFromNodeModules(moduleName: String, directory: String, failedLookupLocations: String[], state: ModuleResolutionState): String = {
    directory = normalizeSlashes(directory)
    while (true) {
      val baseName = getBaseFileName(directory)
      if (baseName != "node_modules") {
        val nodeModulesFolder = combinePaths(directory, "node_modules")
        val nodeModulesFolderExists = directoryProbablyExists(nodeModulesFolder, state.host)
        val candidate = normalizePath(combinePaths(nodeModulesFolder, moduleName))
        // Load only typescript files irrespective of allowJs option if loading from node modules
        var result = loadModuleFromFile(candidate, supportedTypeScriptExtensions, failedLookupLocations, !nodeModulesFolderExists, state)
        if (result) {
          return result
        }
        result = loadNodeModuleFromDirectory(supportedTypeScriptExtensions, candidate, failedLookupLocations, !nodeModulesFolderExists, state)
        if (result) {
          return result
        }
      }

      val parentPath = getDirectoryPath(directory)
      if (parentPath == directory) {
        break
      }

      directory = parentPath
    }
    return ()
  }

  def classicNameResolver(moduleName: String, containingFile: String, compilerOptions: CompilerOptions, host: ModuleResolutionHost): ResolvedModuleWithFailedLookupLocations = {
    val traceEnabled = isTraceEnabled(compilerOptions, host)
    val state = { compilerOptions, host, traceEnabled, skipTsx: !compilerOptions.jsx }
    val failedLookupLocations: String[] = []
    val supportedExtensions = getSupportedExtensions(compilerOptions)
    var containingDirectory = getDirectoryPath(containingFile)

    val resolvedFileName = tryLoadModuleUsingOptionalResolutionSettings(moduleName, containingDirectory, loadModuleFromFile, failedLookupLocations, supportedExtensions, state)
    if (resolvedFileName) {
      return createResolvedModule(resolvedFileName, /*isExternalLibraryImport*/false, failedLookupLocations)
    }

    var referencedSourceFile: String
    while (true) {
      val searchName = normalizePath(combinePaths(containingDirectory, moduleName))
      referencedSourceFile = loadModuleFromFile(searchName, supportedExtensions, failedLookupLocations, /*onlyRecordFailures*/ false, state)
      if (referencedSourceFile) {
        break
      }
      val parentPath = getDirectoryPath(containingDirectory)
      if (parentPath == containingDirectory) {
        break
      }
      containingDirectory = parentPath
    }

    return referencedSourceFile
      ? { resolvedModule: { resolvedFileName: referencedSourceFile  }, failedLookupLocations }
      : { resolvedModule: (), failedLookupLocations }
  }

  /* @internal */
  val defaultInitCompilerOptions: CompilerOptions = {
    module: ModuleKind.CommonJS,
    target: ScriptTarget.ES5,
    noImplicitAny: false,
    sourceMap: false,
  }

  def createCompilerHost(options: CompilerOptions, setParentNodes?: Boolean): CompilerHost = {
    val existingDirectories: Map<Boolean> = {}

    def getCanonicalFileName(fileName: String): String = {
      // if underlying system can distinguish between two files whose names differs only in cases then file name already in canonical form.
      // otherwise use toLowerCase as a canonical form.
      return sys.useCaseSensitiveFileNames ? fileName : fileName.toLowerCase()
    }

    // returned by CScript sys environment
    val unsupportedFileEncodingErrorCode = -2147024809

    def getSourceFile(fileName: String, languageVersion: ScriptTarget, onError?: (message: String) => Unit): SourceFile = {
      var text: String
      try {
        val start = new Date().getTime()
        text = sys.readFile(fileName, options.charset)
        ioReadTime += new Date().getTime() - start
      }
      catch (e) {
        if (onError) {
          onError(e.Int == unsupportedFileEncodingErrorCode
            ? createCompilerDiagnostic(Diagnostics.Unsupported_file_encoding).messageText
            : e.message)
        }
        text = ""
      }

      return text != () ? createSourceFile(fileName, text, languageVersion, setParentNodes) : ()
    }

    def directoryExists(directoryPath: String): Boolean = {
      if (hasProperty(existingDirectories, directoryPath)) {
        return true
      }
      if (sys.directoryExists(directoryPath)) {
        existingDirectories[directoryPath] = true
        return true
      }
      return false
    }

    def ensureDirectoriesExist(directoryPath: String) = {
      if (directoryPath.length > getRootLength(directoryPath) && !directoryExists(directoryPath)) {
        val parentDirectory = getDirectoryPath(directoryPath)
        ensureDirectoriesExist(parentDirectory)
        sys.createDirectory(directoryPath)
      }
    }

    def writeFile(fileName: String, data: String, writeByteOrderMark: Boolean, onError?: (message: String) => Unit) = {
      try {
        val start = new Date().getTime()
        ensureDirectoriesExist(getDirectoryPath(normalizePath(fileName)))
        sys.writeFile(fileName, data, writeByteOrderMark)
        ioWriteTime += new Date().getTime() - start
      }
      catch (e) {
        if (onError) {
          onError(e.message)
        }
      }
    }

    val newLine = getNewLineCharacter(options)

    return {
      getSourceFile,
      getDefaultLibFileName: options => combinePaths(getDirectoryPath(normalizePath(sys.getExecutingFilePath())), getDefaultLibFileName(options)),
      writeFile,
      getCurrentDirectory: memoize(() => sys.getCurrentDirectory()),
      useCaseSensitiveFileNames: () => sys.useCaseSensitiveFileNames,
      getCanonicalFileName,
      getNewLine: () => newLine,
      fileExists: fileName => sys.fileExists(fileName),
      readFile: fileName => sys.readFile(fileName),
      trace: (s: String) => sys.write(s + newLine),
      directoryExists: directoryName => sys.directoryExists(directoryName)
    }
  }

  def getPreEmitDiagnostics(program: Program, sourceFile?: SourceFile, cancellationToken?: CancellationToken): Diagnostic[] {
    var diagnostics = program.getOptionsDiagnostics(cancellationToken).concat(
              program.getSyntacticDiagnostics(sourceFile, cancellationToken),
              program.getGlobalDiagnostics(cancellationToken),
              program.getSemanticDiagnostics(sourceFile, cancellationToken))

    if (program.getCompilerOptions().declaration) {
      diagnostics = diagnostics.concat(program.getDeclarationDiagnostics(sourceFile, cancellationToken))
    }

    return sortAndDeduplicateDiagnostics(diagnostics)
  }

  def flattenDiagnosticMessageText(messageText: String | DiagnosticMessageChain, newLine: String): String = {
    if (typeof messageText == "String") {
      return messageText
    }
    else {
      var diagnosticChain = messageText
      var result = ""

      var indent = 0
      while (diagnosticChain) {
        if (indent) {
          result += newLine

          for (var i = 0; i < indent; i++) {
            result += "  "
          }
        }
        result += diagnosticChain.messageText
        indent++
        diagnosticChain = diagnosticChain.next
      }

      return result
    }
  }

  def createProgram(rootNames: String[], options: CompilerOptions, host?: CompilerHost, oldProgram?: Program): Program = {
    var program: Program
    var files: SourceFile[] = []
    var fileProcessingDiagnostics = createDiagnosticCollection()
    val programDiagnostics = createDiagnosticCollection()

    var commonSourceDirectory: String
    var diagnosticsProducingTypeChecker: TypeChecker
    var noDiagnosticsTypeChecker: TypeChecker
    var classifiableNames: Map<String>

    var skipDefaultLib = options.noLib
    val supportedExtensions = getSupportedExtensions(options)

    val start = new Date().getTime()

    host = host || createCompilerHost(options)
    // Map storing if there is emit blocking diagnostics for given input
    val hasEmitBlockingDiagnostics = createFileMap<Boolean>(getCanonicalFileName)

    val currentDirectory = host.getCurrentDirectory()
    val resolveModuleNamesWorker = host.resolveModuleNames
      ? ((moduleNames: String[], containingFile: String) => host.resolveModuleNames(moduleNames, containingFile))
      : ((moduleNames: String[], containingFile: String) => {
        val resolvedModuleNames: ResolvedModule[] = []
        // resolveModuleName does not store any results between calls.
        // lookup is a local cache to avoid resolving the same module name several times
        val lookup: Map<ResolvedModule> = {}
        for (val moduleName of moduleNames) {
          var resolvedName: ResolvedModule
          if (hasProperty(lookup, moduleName)) {
            resolvedName = lookup[moduleName]
          }
          else {
            resolvedName = resolveModuleName(moduleName, containingFile, options, host).resolvedModule
            lookup[moduleName] = resolvedName
          }
          resolvedModuleNames.push(resolvedName)
        }
        return resolvedModuleNames
      })

    val filesByName = createFileMap<SourceFile>()
    // stores 'filename -> file association' ignoring case
    // used to track cases when two file names differ only in casing
    val filesByNameIgnoreCase = host.useCaseSensitiveFileNames() ? createFileMap<SourceFile>(fileName => fileName.toLowerCase()) : ()

    if (oldProgram) {
      // check properties that can affect structure of the program or module resolution strategy
      // if any of these properties has changed - structure cannot be reused
      val oldOptions = oldProgram.getCompilerOptions()
      if ((oldOptions.module != options.module) ||
        (oldOptions.noResolve != options.noResolve) ||
        (oldOptions.target != options.target) ||
        (oldOptions.noLib != options.noLib) ||
        (oldOptions.jsx != options.jsx) ||
        (oldOptions.allowJs != options.allowJs)) {
        oldProgram = ()
      }
    }

    if (!tryReuseStructureFromOldProgram()) {
      forEach(rootNames, name => processRootFile(name, /*isDefaultLib*/ false))
      // Do not process the default library if:
      //  - The '--noLib' flag is used.
      //  - A 'no-default-lib' reference comment is encountered in
      //    processing the root files.
      if (!skipDefaultLib) {
        processRootFile(host.getDefaultLibFileName(options), /*isDefaultLib*/ true)
      }
    }

    // unconditionally set oldProgram to () to prevent it from being captured in closure
    oldProgram = ()

    program = {
      getRootFileNames: () => rootNames,
      getSourceFile,
      getSourceFiles: () => files,
      getCompilerOptions: () => options,
      getSyntacticDiagnostics,
      getOptionsDiagnostics,
      getGlobalDiagnostics,
      getSemanticDiagnostics,
      getDeclarationDiagnostics,
      getTypeChecker,
      getClassifiableNames,
      getDiagnosticsProducingTypeChecker,
      getCommonSourceDirectory,
      emit,
      getCurrentDirectory: () => currentDirectory,
      getNodeCount: () => getDiagnosticsProducingTypeChecker().getNodeCount(),
      getIdentifierCount: () => getDiagnosticsProducingTypeChecker().getIdentifierCount(),
      getSymbolCount: () => getDiagnosticsProducingTypeChecker().getSymbolCount(),
      getTypeCount: () => getDiagnosticsProducingTypeChecker().getTypeCount(),
      getFileProcessingDiagnostics: () => fileProcessingDiagnostics
    }

    verifyCompilerOptions()

    programTime += new Date().getTime() - start

    return program

    def getCommonSourceDirectory() = {
      if (typeof commonSourceDirectory == "()") {
        if (options.rootDir && checkSourceFilesBelongToPath(files, options.rootDir)) {
          // If a rootDir is specified and is valid use it as the commonSourceDirectory
          commonSourceDirectory = getNormalizedAbsolutePath(options.rootDir, currentDirectory)
        }
        else {
          commonSourceDirectory = computeCommonSourceDirectory(files)
        }
        if (commonSourceDirectory && commonSourceDirectory[commonSourceDirectory.length - 1] != directorySeparator) {
          // Make sure directory path ends with directory separator so this String can directly
          // used to replace with "" to get the relative path of the source file and the relative path doesn't
          // start with / making it rooted path
          commonSourceDirectory += directorySeparator
        }
      }
      return commonSourceDirectory
    }

    def getClassifiableNames() = {
      if (!classifiableNames) {
        // Initialize a checker so that all our files are bound.
        getTypeChecker()
        classifiableNames = {}

        for (val sourceFile of files) {
          copyMap(sourceFile.classifiableNames, classifiableNames)
        }
      }

      return classifiableNames
    }

    def tryReuseStructureFromOldProgram(): Boolean = {
      if (!oldProgram) {
        return false
      }

      Debug.assert(!oldProgram.structureIsReused)

      // there is an old program, check if we can reuse its structure
      val oldRootNames = oldProgram.getRootFileNames()
      if (!arrayIsEqualTo(oldRootNames, rootNames)) {
        return false
      }

      // check if program source files has changed in the way that can affect structure of the program
      val newSourceFiles: SourceFile[] = []
      val filePaths: Path[] = []
      val modifiedSourceFiles: SourceFile[] = []

      for (val oldSourceFile of oldProgram.getSourceFiles()) {
        var newSourceFile = host.getSourceFile(oldSourceFile.fileName, options.target)
        if (!newSourceFile) {
          return false
        }

        newSourceFile.path = oldSourceFile.path
        filePaths.push(newSourceFile.path)

        if (oldSourceFile != newSourceFile) {
          if (oldSourceFile.hasNoDefaultLib != newSourceFile.hasNoDefaultLib) {
            // value of no-default-lib has changed
            // this will affect if default library is injected into the list of files
            return false
          }

          // check tripleslash references
          if (!arrayIsEqualTo(oldSourceFile.referencedFiles, newSourceFile.referencedFiles, fileReferenceIsEqualTo)) {
            // tripleslash references has changed
            return false
          }

          // check imports and module augmentations
          collectExternalModuleReferences(newSourceFile)
          if (!arrayIsEqualTo(oldSourceFile.imports, newSourceFile.imports, moduleNameIsEqualTo)) {
            // imports has changed
            return false
          }
          if (!arrayIsEqualTo(oldSourceFile.moduleAugmentations, newSourceFile.moduleAugmentations, moduleNameIsEqualTo)) {
            // moduleAugmentations has changed
            return false
          }

          if (resolveModuleNamesWorker) {
            val moduleNames = map(concatenate(newSourceFile.imports, newSourceFile.moduleAugmentations), getTextOfLiteral)
            val resolutions = resolveModuleNamesWorker(moduleNames, getNormalizedAbsolutePath(newSourceFile.fileName, currentDirectory))
            // ensure that module resolution results are still correct
            for (var i = 0; i < moduleNames.length; i++) {
              val newResolution = resolutions[i]
              val oldResolution = getResolvedModule(oldSourceFile, moduleNames[i])
              val resolutionChanged = oldResolution
                ? !newResolution ||
                  oldResolution.resolvedFileName != newResolution.resolvedFileName ||
                  !!oldResolution.isExternalLibraryImport != !!newResolution.isExternalLibraryImport
                : newResolution

              if (resolutionChanged) {
                return false
              }
            }
          }
          // pass the cache of module resolutions from the old source file
          newSourceFile.resolvedModules = oldSourceFile.resolvedModules
          modifiedSourceFiles.push(newSourceFile)
        }
        else {
          // file has no changes - use it as is
          newSourceFile = oldSourceFile
        }

        // if file has passed all checks it should be safe to reuse it
        newSourceFiles.push(newSourceFile)
      }

      // update fileName -> file mapping
      for (var i = 0, len = newSourceFiles.length; i < len; i++) {
        filesByName.set(filePaths[i], newSourceFiles[i])
      }

      files = newSourceFiles
      fileProcessingDiagnostics = oldProgram.getFileProcessingDiagnostics()

      for (val modifiedFile of modifiedSourceFiles) {
        fileProcessingDiagnostics.reattachFileDiagnostics(modifiedFile)
      }
      oldProgram.structureIsReused = true

      return true
    }

    def getEmitHost(writeFileCallback?: WriteFileCallback): EmitHost = {
      return {
        getCanonicalFileName,
        getCommonSourceDirectory: program.getCommonSourceDirectory,
        getCompilerOptions: program.getCompilerOptions,
        getCurrentDirectory: () => currentDirectory,
        getNewLine: () => host.getNewLine(),
        getSourceFile: program.getSourceFile,
        getSourceFiles: program.getSourceFiles,
        writeFile: writeFileCallback || (
          (fileName, data, writeByteOrderMark, onError) => host.writeFile(fileName, data, writeByteOrderMark, onError)),
        isEmitBlocked,
      }
    }

    def getDiagnosticsProducingTypeChecker() = {
      return diagnosticsProducingTypeChecker || (diagnosticsProducingTypeChecker = createTypeChecker(program, /*produceDiagnostics:*/ true))
    }

    def getTypeChecker() = {
      return noDiagnosticsTypeChecker || (noDiagnosticsTypeChecker = createTypeChecker(program, /*produceDiagnostics:*/ false))
    }

    def emit(sourceFile?: SourceFile, writeFileCallback?: WriteFileCallback, cancellationToken?: CancellationToken): EmitResult = {
      return runWithCancellationToken(() => emitWorker(this, sourceFile, writeFileCallback, cancellationToken))
    }

    def isEmitBlocked(emitFileName: String): Boolean = {
      return hasEmitBlockingDiagnostics.contains(toPath(emitFileName, currentDirectory, getCanonicalFileName))
    }

    def emitWorker(program: Program, sourceFile: SourceFile, writeFileCallback: WriteFileCallback, cancellationToken: CancellationToken): EmitResult = {
      // If the noEmitOnError flag is set, then check if we have any errors so far.  If so,
      // immediately bail out.  Note that we pass '()' for 'sourceFile' so that we
      // get any preEmit diagnostics, not just the ones
      if (options.noEmitOnError) {
        val preEmitDiagnostics = getPreEmitDiagnostics(program, /*sourceFile:*/ (), cancellationToken)
        if (preEmitDiagnostics.length > 0) {
          return { diagnostics: preEmitDiagnostics, sourceMaps: (), emitSkipped: true }
        }
      }

      // Create the emit resolver outside of the "emitTime" tracking code below.  That way
      // any cost associated with it (like type checking) are appropriate associated with
      // the type-checking counter.
      //
      // If the -out option is specified, we should not pass the source file to getEmitResolver.
      // This is because in the -out scenario all files need to be emitted, and therefore all
      // files need to be type checked. And the way to specify that all files need to be type
      // checked is to not pass the file to getEmitResolver.
      val emitResolver = getDiagnosticsProducingTypeChecker().getEmitResolver((options.outFile || options.out) ? () : sourceFile)

      val start = new Date().getTime()

      val emitResult = emitFiles(
        emitResolver,
        getEmitHost(writeFileCallback),
        sourceFile)

      emitTime += new Date().getTime() - start
      return emitResult
    }

    def getSourceFile(fileName: String): SourceFile = {
      return filesByName.get(toPath(fileName, currentDirectory, getCanonicalFileName))
    }

    def getDiagnosticsHelper(
        sourceFile: SourceFile,
        getDiagnostics: (sourceFile: SourceFile, cancellationToken: CancellationToken) => Diagnostic[],
        cancellationToken: CancellationToken): Diagnostic[] {
      if (sourceFile) {
        return getDiagnostics(sourceFile, cancellationToken)
      }

      val allDiagnostics: Diagnostic[] = []
      forEach(program.getSourceFiles(), sourceFile => {
        if (cancellationToken) {
          cancellationToken.throwIfCancellationRequested()
        }
        addRange(allDiagnostics, getDiagnostics(sourceFile, cancellationToken))
      })

      return sortAndDeduplicateDiagnostics(allDiagnostics)
    }

    def getSyntacticDiagnostics(sourceFile: SourceFile, cancellationToken: CancellationToken): Diagnostic[] {
      return getDiagnosticsHelper(sourceFile, getSyntacticDiagnosticsForFile, cancellationToken)
    }

    def getSemanticDiagnostics(sourceFile: SourceFile, cancellationToken: CancellationToken): Diagnostic[] {
      return getDiagnosticsHelper(sourceFile, getSemanticDiagnosticsForFile, cancellationToken)
    }

    def getDeclarationDiagnostics(sourceFile: SourceFile, cancellationToken: CancellationToken): Diagnostic[] {
      return getDiagnosticsHelper(sourceFile, getDeclarationDiagnosticsForFile, cancellationToken)
    }

    def getSyntacticDiagnosticsForFile(sourceFile: SourceFile, cancellationToken: CancellationToken): Diagnostic[] {
      return sourceFile.parseDiagnostics
    }

    def runWithCancellationToken<T>(func: () => T): T = {
      try {
        return func()
      }
      catch (e) {
        if (e instanceof OperationCanceledException) {
          // We were canceled while performing the operation.  Because our type checker
          // might be a bad state, we need to throw it away.
          //
          // Note: we are overly aggressive here.  We do not actually *have* to throw away
          // the "noDiagnosticsTypeChecker".  However, for simplicity, i'd like to keep
          // the lifetimes of these two TypeCheckers the same.  Also, we generally only
          // cancel when the user has made a change anyways.  And, in that case, we (the
          // program instance) will get thrown away anyways.  So trying to keep one of
          // these type checkers alive doesn't serve much purpose.
          noDiagnosticsTypeChecker = ()
          diagnosticsProducingTypeChecker = ()
        }

        throw e
      }
    }

    def getSemanticDiagnosticsForFile(sourceFile: SourceFile, cancellationToken: CancellationToken): Diagnostic[] {
      return runWithCancellationToken(() => {
        val typeChecker = getDiagnosticsProducingTypeChecker()

        Debug.assert(!!sourceFile.bindDiagnostics)
        val bindDiagnostics = sourceFile.bindDiagnostics
        // For JavaScript files, we don't want to report the normal typescript semantic errors.
        // Instead, we just report errors for using TypeScript-only constructs from within a
        // JavaScript file.
        val checkDiagnostics = isSourceFileJavaScript(sourceFile) ?
          getJavaScriptSemanticDiagnosticsForFile(sourceFile, cancellationToken) :
          typeChecker.getDiagnostics(sourceFile, cancellationToken)
        val fileProcessingDiagnosticsInFile = fileProcessingDiagnostics.getDiagnostics(sourceFile.fileName)
        val programDiagnosticsInFile = programDiagnostics.getDiagnostics(sourceFile.fileName)

        return bindDiagnostics.concat(checkDiagnostics).concat(fileProcessingDiagnosticsInFile).concat(programDiagnosticsInFile)
      })
    }

    def getJavaScriptSemanticDiagnosticsForFile(sourceFile: SourceFile, cancellationToken: CancellationToken): Diagnostic[] {
      return runWithCancellationToken(() => {
        val diagnostics: Diagnostic[] = []
        walk(sourceFile)

        return diagnostics

        def walk(node: Node): Boolean = {
          if (!node) {
            return false
          }

          switch (node.kind) {
            case SyntaxKind.ImportEqualsDeclaration:
              diagnostics.push(createDiagnosticForNode(node, Diagnostics.import_can_only_be_used_in_a_ts_file))
              return true
            case SyntaxKind.ExportAssignment:
              if ((<ExportAssignment>node).isExportEquals) {
                diagnostics.push(createDiagnosticForNode(node, Diagnostics.export_can_only_be_used_in_a_ts_file))
                return true
              }
              break
            case SyntaxKind.ClassDeclaration:
              var classDeclaration = <ClassDeclaration>node
              if (checkModifiers(classDeclaration.modifiers) ||
                checkTypeParameters(classDeclaration.typeParameters)) {
                return true
              }
              break
            case SyntaxKind.HeritageClause:
              var heritageClause = <HeritageClause>node
              if (heritageClause.token == SyntaxKind.ImplementsKeyword) {
                diagnostics.push(createDiagnosticForNode(node, Diagnostics.implements_clauses_can_only_be_used_in_a_ts_file))
                return true
              }
              break
            case SyntaxKind.InterfaceDeclaration:
              diagnostics.push(createDiagnosticForNode(node, Diagnostics.interface_declarations_can_only_be_used_in_a_ts_file))
              return true
            case SyntaxKind.ModuleDeclaration:
              diagnostics.push(createDiagnosticForNode(node, Diagnostics.module_declarations_can_only_be_used_in_a_ts_file))
              return true
            case SyntaxKind.TypeAliasDeclaration:
              diagnostics.push(createDiagnosticForNode(node, Diagnostics.type_aliases_can_only_be_used_in_a_ts_file))
              return true
            case SyntaxKind.MethodDeclaration:
            case SyntaxKind.MethodSignature:
            case SyntaxKind.Constructor:
            case SyntaxKind.GetAccessor:
            case SyntaxKind.SetAccessor:
            case SyntaxKind.FunctionExpression:
            case SyntaxKind.FunctionDeclaration:
            case SyntaxKind.ArrowFunction:
            case SyntaxKind.FunctionDeclaration:
              val functionDeclaration = <FunctionLikeDeclaration>node
              if (checkModifiers(functionDeclaration.modifiers) ||
                checkTypeParameters(functionDeclaration.typeParameters) ||
                checkTypeAnnotation(functionDeclaration.type)) {
                return true
              }
              break
            case SyntaxKind.VariableStatement:
              val variableStatement = <VariableStatement>node
              if (checkModifiers(variableStatement.modifiers)) {
                return true
              }
              break
            case SyntaxKind.VariableDeclaration:
              val variableDeclaration = <VariableDeclaration>node
              if (checkTypeAnnotation(variableDeclaration.type)) {
                return true
              }
              break
            case SyntaxKind.CallExpression:
            case SyntaxKind.NewExpression:
              val expression = <CallExpression>node
              if (expression.typeArguments && expression.typeArguments.length > 0) {
                val start = expression.typeArguments.pos
                diagnostics.push(createFileDiagnostic(sourceFile, start, expression.typeArguments.end - start,
                  Diagnostics.type_arguments_can_only_be_used_in_a_ts_file))
                return true
              }
              break
            case SyntaxKind.Parameter:
              val parameter = <ParameterDeclaration>node
              if (parameter.modifiers) {
                val start = parameter.modifiers.pos
                diagnostics.push(createFileDiagnostic(sourceFile, start, parameter.modifiers.end - start,
                  Diagnostics.parameter_modifiers_can_only_be_used_in_a_ts_file))
                return true
              }
              if (parameter.questionToken) {
                diagnostics.push(createDiagnosticForNode(parameter.questionToken, Diagnostics._0_can_only_be_used_in_a_ts_file, "?"))
                return true
              }
              if (parameter.type) {
                diagnostics.push(createDiagnosticForNode(parameter.type, Diagnostics.types_can_only_be_used_in_a_ts_file))
                return true
              }
              break
            case SyntaxKind.PropertyDeclaration:
              diagnostics.push(createDiagnosticForNode(node, Diagnostics.property_declarations_can_only_be_used_in_a_ts_file))
              return true
            case SyntaxKind.EnumDeclaration:
              diagnostics.push(createDiagnosticForNode(node, Diagnostics.enum_declarations_can_only_be_used_in_a_ts_file))
              return true
            case SyntaxKind.TypeAssertionExpression:
              var typeAssertionExpression = <TypeAssertion>node
              diagnostics.push(createDiagnosticForNode(typeAssertionExpression.type, Diagnostics.type_assertion_expressions_can_only_be_used_in_a_ts_file))
              return true
            case SyntaxKind.Decorator:
              diagnostics.push(createDiagnosticForNode(node, Diagnostics.decorators_can_only_be_used_in_a_ts_file))
              return true
          }

          return forEachChild(node, walk)
        }

        def checkTypeParameters(typeParameters: NodeArray<TypeParameterDeclaration>): Boolean = {
          if (typeParameters) {
            val start = typeParameters.pos
            diagnostics.push(createFileDiagnostic(sourceFile, start, typeParameters.end - start, Diagnostics.type_parameter_declarations_can_only_be_used_in_a_ts_file))
            return true
          }
          return false
        }

        def checkTypeAnnotation(type: TypeNode): Boolean = {
          if (type) {
            diagnostics.push(createDiagnosticForNode(type, Diagnostics.types_can_only_be_used_in_a_ts_file))
            return true
          }

          return false
        }

        def checkModifiers(modifiers: ModifiersArray): Boolean = {
          if (modifiers) {
            for (val modifier of modifiers) {
              switch (modifier.kind) {
                case SyntaxKind.PublicKeyword:
                case SyntaxKind.PrivateKeyword:
                case SyntaxKind.ProtectedKeyword:
                case SyntaxKind.ReadonlyKeyword:
                case SyntaxKind.DeclareKeyword:
                  diagnostics.push(createDiagnosticForNode(modifier, Diagnostics._0_can_only_be_used_in_a_ts_file, tokenToString(modifier.kind)))
                  return true

                // These are all legal modifiers.
                case SyntaxKind.StaticKeyword:
                case SyntaxKind.ExportKeyword:
                case SyntaxKind.ConstKeyword:
                case SyntaxKind.DefaultKeyword:
                case SyntaxKind.AbstractKeyword:
              }
            }
          }

          return false
        }
      })
    }

    def getDeclarationDiagnosticsForFile(sourceFile: SourceFile, cancellationToken: CancellationToken): Diagnostic[] {
      return runWithCancellationToken(() => {
        if (!isDeclarationFile(sourceFile)) {
          val resolver = getDiagnosticsProducingTypeChecker().getEmitResolver(sourceFile, cancellationToken)
          // Don't actually write any files since we're just getting diagnostics.
          val writeFile: WriteFileCallback = () => { }
          return ts.getDeclarationDiagnostics(getEmitHost(writeFile), resolver, sourceFile)
        }
      })
    }

    def getOptionsDiagnostics(): Diagnostic[] {
      val allDiagnostics: Diagnostic[] = []
      addRange(allDiagnostics, fileProcessingDiagnostics.getGlobalDiagnostics())
      addRange(allDiagnostics, programDiagnostics.getGlobalDiagnostics())
      return sortAndDeduplicateDiagnostics(allDiagnostics)
    }

    def getGlobalDiagnostics(): Diagnostic[] {
      val allDiagnostics: Diagnostic[] = []
      addRange(allDiagnostics, getDiagnosticsProducingTypeChecker().getGlobalDiagnostics())
      return sortAndDeduplicateDiagnostics(allDiagnostics)
    }

    def hasExtension(fileName: String): Boolean = {
      return getBaseFileName(fileName).indexOf(".") >= 0
    }

    def processRootFile(fileName: String, isDefaultLib: Boolean) = {
      processSourceFile(normalizePath(fileName), isDefaultLib)
    }

    def fileReferenceIsEqualTo(a: FileReference, b: FileReference): Boolean = {
      return a.fileName == b.fileName
    }

    def moduleNameIsEqualTo(a: LiteralExpression, b: LiteralExpression): Boolean = {
      return a.text == b.text
    }

    def getTextOfLiteral(literal: LiteralExpression): String = {
      return literal.text
    }

    def collectExternalModuleReferences(file: SourceFile): Unit = {
      if (file.imports) {
        return
      }

      val isJavaScriptFile = isSourceFileJavaScript(file)
      val isExternalModuleFile = isExternalModule(file)

      var imports: LiteralExpression[]
      var moduleAugmentations: LiteralExpression[]

      for (val node of file.statements) {
        collectModuleReferences(node, /*inAmbientModule*/ false)
        if (isJavaScriptFile) {
          collectRequireCalls(node)
        }
      }

      file.imports = imports || emptyArray
      file.moduleAugmentations = moduleAugmentations || emptyArray

      return

      def collectModuleReferences(node: Node, inAmbientModule: Boolean): Unit = {
        switch (node.kind) {
          case SyntaxKind.ImportDeclaration:
          case SyntaxKind.ImportEqualsDeclaration:
          case SyntaxKind.ExportDeclaration:
            var moduleNameExpr = getExternalModuleName(node)
            if (!moduleNameExpr || moduleNameExpr.kind != SyntaxKind.StringLiteral) {
              break
            }
            if (!(<LiteralExpression>moduleNameExpr).text) {
              break
            }

            // TypeScript 1.0 spec (April 2014): 12.1.6
            // An ExternalImportDeclaration in an AmbientExternalModuleDeclaration may reference other external modules
            // only through top - level external module names. Relative external module names are not permitted.
            if (!inAmbientModule || !isExternalModuleNameRelative((<LiteralExpression>moduleNameExpr).text)) {
              (imports || (imports = [])).push(<LiteralExpression>moduleNameExpr)
            }
            break
          case SyntaxKind.ModuleDeclaration:
            if (isAmbientModule(<ModuleDeclaration>node) && (inAmbientModule || node.flags & NodeFlags.Ambient || isDeclarationFile(file))) {
              val moduleName = <LiteralExpression>(<ModuleDeclaration>node).name
              // Ambient module declarations can be interpreted as augmentations for some existing external modules.
              // This will happen in two cases:
              // - if current file is external module then module augmentation is a ambient module declaration defined in the top level scope
              // - if current file is not external module then module augmentation is an ambient module declaration with non-relative module name
              //   immediately nested in top level ambient module declaration .
              if (isExternalModuleFile || (inAmbientModule && !isExternalModuleNameRelative(moduleName.text))) {
                (moduleAugmentations || (moduleAugmentations = [])).push(moduleName)
              }
              else if (!inAmbientModule) {
                // An AmbientExternalModuleDeclaration declares an external module.
                // This type of declaration is permitted only in the global module.
                // The StringLiteral must specify a top - level external module name.
                // Relative external module names are not permitted

                // NOTE: body of ambient module is always a module block
                for (val statement of (<ModuleBlock>(<ModuleDeclaration>node).body).statements) {
                  collectModuleReferences(statement, /*inAmbientModule*/ true)
                }
              }
            }
        }
      }

      def collectRequireCalls(node: Node): Unit = {
        if (isRequireCall(node, /*checkArgumentIsStringLiteral*/true)) {
          (imports || (imports = [])).push(<StringLiteral>(<CallExpression>node).arguments[0])
        }
        else {
          forEachChild(node, collectRequireCalls)
        }
      }
    }

    def processSourceFile(fileName: String, isDefaultLib: Boolean, refFile?: SourceFile, refPos?: Int, refEnd?: Int) = {
      var diagnosticArgument: String[]
      var diagnostic: DiagnosticMessage
      if (hasExtension(fileName)) {
        if (!options.allowNonTsExtensions && !forEach(supportedExtensions, extension => fileExtensionIs(host.getCanonicalFileName(fileName), extension))) {
          diagnostic = Diagnostics.File_0_has_unsupported_extension_The_only_supported_extensions_are_1
          diagnosticArgument = [fileName, "'" + supportedExtensions.join("', '") + "'"]
        }
        else if (!findSourceFile(fileName, toPath(fileName, currentDirectory, getCanonicalFileName), isDefaultLib, refFile, refPos, refEnd)) {
          diagnostic = Diagnostics.File_0_not_found
          diagnosticArgument = [fileName]
        }
        else if (refFile && host.getCanonicalFileName(fileName) == host.getCanonicalFileName(refFile.fileName)) {
          diagnostic = Diagnostics.A_file_cannot_have_a_reference_to_itself
          diagnosticArgument = [fileName]
        }
      }
      else {
        val nonTsFile: SourceFile = options.allowNonTsExtensions && findSourceFile(fileName, toPath(fileName, currentDirectory, getCanonicalFileName), isDefaultLib, refFile, refPos, refEnd)
        if (!nonTsFile) {
          if (options.allowNonTsExtensions) {
            diagnostic = Diagnostics.File_0_not_found
            diagnosticArgument = [fileName]
          }
          else if (!forEach(supportedExtensions, extension => findSourceFile(fileName + extension, toPath(fileName + extension, currentDirectory, getCanonicalFileName), isDefaultLib, refFile, refPos, refEnd))) {
            diagnostic = Diagnostics.File_0_not_found
            fileName += ".ts"
            diagnosticArgument = [fileName]
          }
        }
      }

      if (diagnostic) {
        if (refFile != () && refEnd != () && refPos != ()) {
          fileProcessingDiagnostics.add(createFileDiagnostic(refFile, refPos, refEnd - refPos, diagnostic, ...diagnosticArgument))
        }
        else {
          fileProcessingDiagnostics.add(createCompilerDiagnostic(diagnostic, ...diagnosticArgument))
        }
      }
    }

    def reportFileNamesDifferOnlyInCasingError(fileName: String, existingFileName: String, refFile: SourceFile, refPos: Int, refEnd: Int): Unit = {
      if (refFile != () && refPos != () && refEnd != ()) {
        fileProcessingDiagnostics.add(createFileDiagnostic(refFile, refPos, refEnd - refPos,
          Diagnostics.File_name_0_differs_from_already_included_file_name_1_only_in_casing, fileName, existingFileName))
      }
      else {
        fileProcessingDiagnostics.add(createCompilerDiagnostic(Diagnostics.File_name_0_differs_from_already_included_file_name_1_only_in_casing, fileName, existingFileName))
      }
    }

    // Get source file from normalized fileName
    def findSourceFile(fileName: String, path: Path, isDefaultLib: Boolean, refFile?: SourceFile, refPos?: Int, refEnd?: Int): SourceFile = {
      if (filesByName.contains(path)) {
        val file = filesByName.get(path)
        // try to check if we've already seen this file but with a different casing in path
        // NOTE: this only makes sense for case-insensitive file systems
        if (file && options.forceConsistentCasingInFileNames && getNormalizedAbsolutePath(file.fileName, currentDirectory) != getNormalizedAbsolutePath(fileName, currentDirectory)) {
          reportFileNamesDifferOnlyInCasingError(fileName, file.fileName, refFile, refPos, refEnd)
        }

        return file
      }

      // We haven't looked for this file, do so now and cache result
      val file = host.getSourceFile(fileName, options.target, hostErrorMessage => {
        if (refFile != () && refPos != () && refEnd != ()) {
          fileProcessingDiagnostics.add(createFileDiagnostic(refFile, refPos, refEnd - refPos,
            Diagnostics.Cannot_read_file_0_Colon_1, fileName, hostErrorMessage))
        }
        else {
          fileProcessingDiagnostics.add(createCompilerDiagnostic(Diagnostics.Cannot_read_file_0_Colon_1, fileName, hostErrorMessage))
        }
      })

      filesByName.set(path, file)
      if (file) {
        file.path = path

        if (host.useCaseSensitiveFileNames()) {
          // for case-sensitive file systems check if we've already seen some file with similar filename ignoring case
          val existingFile = filesByNameIgnoreCase.get(path)
          if (existingFile) {
            reportFileNamesDifferOnlyInCasingError(fileName, existingFile.fileName, refFile, refPos, refEnd)
          }
          else {
            filesByNameIgnoreCase.set(path, file)
          }
        }

        skipDefaultLib = skipDefaultLib || file.hasNoDefaultLib

        val basePath = getDirectoryPath(fileName)
        if (!options.noResolve) {
          processReferencedFiles(file, basePath)
        }

        // always process imported modules to record module name resolutions
        processImportedModules(file, basePath)

        if (isDefaultLib) {
          files.unshift(file)
        }
        else {
          files.push(file)
        }
      }

      return file
    }

    def processReferencedFiles(file: SourceFile, basePath: String) = {
      forEach(file.referencedFiles, ref => {
        val referencedFileName = resolveTripleslashReference(ref.fileName, file.fileName)
        processSourceFile(referencedFileName, /*isDefaultLib*/ false, file, ref.pos, ref.end)
      })
    }

    def getCanonicalFileName(fileName: String): String = {
      return host.getCanonicalFileName(fileName)
    }

    def processImportedModules(file: SourceFile, basePath: String) = {
      collectExternalModuleReferences(file)
      if (file.imports.length || file.moduleAugmentations.length) {
        file.resolvedModules = {}
        val moduleNames = map(concatenate(file.imports, file.moduleAugmentations), getTextOfLiteral)
        val resolutions = resolveModuleNamesWorker(moduleNames, getNormalizedAbsolutePath(file.fileName, currentDirectory))
        for (var i = 0; i < moduleNames.length; i++) {
          val resolution = resolutions[i]
          setResolvedModule(file, moduleNames[i], resolution)
          // add file to program only if:
          // - resolution was successful
          // - noResolve is falsy
          // - module name come from the list fo imports
          val shouldAddFile = resolution &&
            !options.noResolve &&
            i < file.imports.length

          if (shouldAddFile) {
            val importedFile = findSourceFile(resolution.resolvedFileName, toPath(resolution.resolvedFileName, currentDirectory, getCanonicalFileName), /*isDefaultLib*/ false, file, skipTrivia(file.text, file.imports[i].pos), file.imports[i].end)

            if (importedFile && resolution.isExternalLibraryImport) {
              // Since currently irrespective of allowJs, we only look for supportedTypeScript extension external module files,
              // this check is ok. Otherwise this would be never true for javascript file
              if (!isExternalModule(importedFile) && importedFile.statements.length) {
                val start = getTokenPosOfNode(file.imports[i], file)
                fileProcessingDiagnostics.add(createFileDiagnostic(file, start, file.imports[i].end - start, Diagnostics.Exported_external_package_typings_file_0_is_not_a_module_Please_contact_the_package_author_to_update_the_package_definition, importedFile.fileName))
              }
              else if (importedFile.referencedFiles.length) {
                val firstRef = importedFile.referencedFiles[0]
                fileProcessingDiagnostics.add(createFileDiagnostic(importedFile, firstRef.pos, firstRef.end - firstRef.pos, Diagnostics.Exported_external_package_typings_file_cannot_contain_tripleslash_references_Please_contact_the_package_author_to_update_the_package_definition))
              }
            }
          }
        }
      }
      else {
        // no imports - drop cached module resolutions
        file.resolvedModules = ()
      }
      return
    }

    def computeCommonSourceDirectory(sourceFiles: SourceFile[]): String = {
      var commonPathComponents: String[]
      val failed = forEach(files, sourceFile => {
        // Each file contributes into common source file path
        if (isDeclarationFile(sourceFile)) {
          return
        }

        val sourcePathComponents = getNormalizedPathComponents(sourceFile.fileName, currentDirectory)
        sourcePathComponents.pop(); // The base file name is not part of the common directory path

        if (!commonPathComponents) {
          // first file
          commonPathComponents = sourcePathComponents
          return
        }

        for (var i = 0, n = Math.min(commonPathComponents.length, sourcePathComponents.length); i < n; i++) {
          if (getCanonicalFileName(commonPathComponents[i]) != getCanonicalFileName(sourcePathComponents[i])) {
            if (i == 0) {
              // Failed to find any common path component
              return true
            }

            // New common path found that is 0 -> i-1
            commonPathComponents.length = i
            break
          }
        }

        // If the sourcePathComponents was shorter than the commonPathComponents, truncate to the sourcePathComponents
        if (sourcePathComponents.length < commonPathComponents.length) {
          commonPathComponents.length = sourcePathComponents.length
        }
      })

      // A common path can not be found when paths span multiple drives on windows, for example
      if (failed) {
        return ""
      }

      if (!commonPathComponents) { // Can happen when all input files are .d.ts files
        return currentDirectory
      }

      return getNormalizedPathFromPathComponents(commonPathComponents)
    }

    def checkSourceFilesBelongToPath(sourceFiles: SourceFile[], rootDirectory: String): Boolean = {
      var allFilesBelongToPath = true
      if (sourceFiles) {
        val absoluteRootDirectoryPath = host.getCanonicalFileName(getNormalizedAbsolutePath(rootDirectory, currentDirectory))

        for (val sourceFile of sourceFiles) {
          if (!isDeclarationFile(sourceFile)) {
            val absoluteSourceFilePath = host.getCanonicalFileName(getNormalizedAbsolutePath(sourceFile.fileName, currentDirectory))
            if (absoluteSourceFilePath.indexOf(absoluteRootDirectoryPath) != 0) {
              programDiagnostics.add(createCompilerDiagnostic(Diagnostics.File_0_is_not_under_rootDir_1_rootDir_is_expected_to_contain_all_source_files, sourceFile.fileName, options.rootDir))
              allFilesBelongToPath = false
            }
          }
        }
      }

      return allFilesBelongToPath
    }

    def verifyCompilerOptions() = {
      if (options.isolatedModules) {
        if (options.declaration) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "declaration", "isolatedModules"))
        }

        if (options.noEmitOnError) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "noEmitOnError", "isolatedModules"))
        }

        if (options.out) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "out", "isolatedModules"))
        }

        if (options.outFile) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "outFile", "isolatedModules"))
        }
      }

      if (options.inlineSourceMap) {
        if (options.sourceMap) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "sourceMap", "inlineSourceMap"))
        }
        if (options.mapRoot) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "mapRoot", "inlineSourceMap"))
        }
      }

      if (options.paths && options.baseUrl == ()) {
        programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_paths_cannot_be_used_without_specifying_baseUrl_option))
      }

      if (options.paths) {
        for (val key in options.paths) {
          if (!hasProperty(options.paths, key)) {
            continue
          }
          if (!hasZeroOrOneAsteriskCharacter(key)) {
            programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Pattern_0_can_have_at_most_one_Asterisk_character, key))
          }
          for (val subst of options.paths[key]) {
            if (!hasZeroOrOneAsteriskCharacter(subst)) {
              programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Substitution_0_in_pattern_1_in_can_have_at_most_one_Asterisk_character, subst, key))
            }
          }
        }
      }

      if (options.inlineSources) {
        if (!options.sourceMap && !options.inlineSourceMap) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_inlineSources_can_only_be_used_when_either_option_inlineSourceMap_or_option_sourceMap_is_provided))
        }
        if (options.sourceRoot) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "sourceRoot", "inlineSources"))
        }
      }

      if (options.out && options.outFile) {
        programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "out", "outFile"))
      }

      if (!options.sourceMap && (options.mapRoot || options.sourceRoot)) {
        // Error to specify --mapRoot or --sourceRoot without mapSourceFiles
        if (options.mapRoot) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_without_specifying_option_1, "mapRoot", "sourceMap"))
        }
        if (options.sourceRoot && !options.inlineSourceMap) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_without_specifying_option_1, "sourceRoot", "sourceMap"))
        }
      }

      val languageVersion = options.target || ScriptTarget.ES3
      val outFile = options.outFile || options.out

      val firstExternalModuleSourceFile = forEach(files, f => isExternalModule(f) ? f : ())
      if (options.isolatedModules) {
        if (options.module == ModuleKind.None && languageVersion < ScriptTarget.ES6) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_isolatedModules_can_only_be_used_when_either_option_module_is_provided_or_option_target_is_ES2015_or_higher))
        }

        val firstNonExternalModuleSourceFile = forEach(files, f => !isExternalModule(f) && !isDeclarationFile(f) ? f : ())
        if (firstNonExternalModuleSourceFile) {
          val span = getErrorSpanForNode(firstNonExternalModuleSourceFile, firstNonExternalModuleSourceFile)
          programDiagnostics.add(createFileDiagnostic(firstNonExternalModuleSourceFile, span.start, span.length, Diagnostics.Cannot_compile_namespaces_when_the_isolatedModules_flag_is_provided))
        }
      }
      else if (firstExternalModuleSourceFile && languageVersion < ScriptTarget.ES6 && options.module == ModuleKind.None) {
        // We cannot use createDiagnosticFromNode because nodes do not have parents yet
        val span = getErrorSpanForNode(firstExternalModuleSourceFile, firstExternalModuleSourceFile.externalModuleIndicator)
        programDiagnostics.add(createFileDiagnostic(firstExternalModuleSourceFile, span.start, span.length, Diagnostics.Cannot_compile_modules_unless_the_module_flag_is_provided_with_a_valid_module_type_Consider_setting_the_module_compiler_option_in_a_tsconfig_json_file))
      }

      // Cannot specify module gen target of es6 when below es6
      if (options.module == ModuleKind.ES6 && languageVersion < ScriptTarget.ES6) {
        programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Cannot_compile_modules_into_es2015_when_targeting_ES5_or_lower))
      }

      // Cannot specify module gen that isn't amd or system with --out
      if (outFile && options.module && !(options.module == ModuleKind.AMD || options.module == ModuleKind.System)) {
        programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Only_amd_and_system_modules_are_supported_alongside_0, options.out ? "out" : "outFile"))
      }

      // there has to be common source directory if user specified --outdir || --sourceRoot
      // if user specified --mapRoot, there needs to be common source directory if there would be multiple files being emitted
      if (options.outDir || // there is --outDir specified
        options.sourceRoot || // there is --sourceRoot specified
        options.mapRoot) { // there is --mapRoot specified

        // Precalculate and cache the common source directory
        val dir = getCommonSourceDirectory()

        // If we failed to find a good common directory, but outDir is specified and at least one of our files is on a windows drive/URL/other resource, add a failure
        if (options.outDir && dir == "" && forEach(files, file => getRootLength(file.fileName) > 1)) {
            programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Cannot_find_the_common_subdirectory_path_for_the_input_files))
        }
      }

      if (options.noEmit) {
        if (options.out) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "noEmit", "out"))
        }

        if (options.outFile) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "noEmit", "outFile"))
        }

        if (options.outDir) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "noEmit", "outDir"))
        }

        if (options.declaration) {
          programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "noEmit", "declaration"))
        }
      }
      else if (options.allowJs && options.declaration) {
        programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_with_option_1, "allowJs", "declaration"))
      }

      if (options.emitDecoratorMetadata &&
        !options.experimentalDecorators) {
        programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Option_0_cannot_be_specified_without_specifying_option_1, "emitDecoratorMetadata", "experimentalDecorators"))
      }

      if (options.reactNamespace && !isIdentifier(options.reactNamespace, languageVersion)) {
        programDiagnostics.add(createCompilerDiagnostic(Diagnostics.Invalid_value_for_reactNamespace_0_is_not_a_valid_identifier, options.reactNamespace))
      }

      // If the emit is enabled make sure that every output file is unique and not overwriting any of the input files
      if (!options.noEmit && !options.suppressOutputPathCheck) {
        val emitHost = getEmitHost()
        val emitFilesSeen = createFileMap<Boolean>(!host.useCaseSensitiveFileNames() ? key => key.toLocaleLowerCase() : ())
        forEachExpectedEmitFile(emitHost, (emitFileNames, sourceFiles, isBundledEmit) => {
          verifyEmitFilePath(emitFileNames.jsFilePath, emitFilesSeen)
          verifyEmitFilePath(emitFileNames.declarationFilePath, emitFilesSeen)
        })
      }

      // Verify that all the emit files are unique and don't overwrite input files
      def verifyEmitFilePath(emitFileName: String, emitFilesSeen: FileMap<Boolean>) = {
        if (emitFileName) {
          val emitFilePath = toPath(emitFileName, currentDirectory, getCanonicalFileName)
          // Report error if the output overwrites input file
          if (filesByName.contains(emitFilePath)) {
            createEmitBlockingDiagnostics(emitFileName, emitFilePath, Diagnostics.Cannot_write_file_0_because_it_would_overwrite_input_file)
          }

          // Report error if multiple files write into same file
          if (emitFilesSeen.contains(emitFilePath)) {
            // Already seen the same emit file - report error
            createEmitBlockingDiagnostics(emitFileName, emitFilePath, Diagnostics.Cannot_write_file_0_because_it_would_be_overwritten_by_multiple_input_files)
          }
          else {
            emitFilesSeen.set(emitFilePath, true)
          }
        }
      }
    }

    def createEmitBlockingDiagnostics(emitFileName: String, emitFilePath: Path, message: DiagnosticMessage) = {
      hasEmitBlockingDiagnostics.set(toPath(emitFileName, currentDirectory, getCanonicalFileName), true)
      programDiagnostics.add(createCompilerDiagnostic(message, emitFileName))
    }
  }
}
