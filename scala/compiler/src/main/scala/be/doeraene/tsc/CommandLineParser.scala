/// <reference path="sys.ts"/>
/// <reference path="types.ts"/>
/// <reference path="core.ts"/>
/// <reference path="diagnosticInformationMap.generated.ts"/>
/// <reference path="scanner.ts"/>

package ts {
  /* @internal */
  var optionDeclarations: CommandLineOption[] = [
    {
      name: "charset",
      type: "String",
    },
    {
      name: "declaration",
      shortName: "d",
      type: "Boolean",
      description: Diagnostics.Generates_corresponding_d_ts_file,
    },
    {
      name: "diagnostics",
      type: "Boolean",
    },
    {
      name: "emitBOM",
      type: "Boolean"
    },
    {
      name: "help",
      shortName: "h",
      type: "Boolean",
      description: Diagnostics.Print_this_message,
    },
    {
      name: "init",
      type: "Boolean",
      description: Diagnostics.Initializes_a_TypeScript_project_and_creates_a_tsconfig_json_file,
    },
    {
      name: "inlineSourceMap",
      type: "Boolean",
    },
    {
      name: "inlineSources",
      type: "Boolean",
    },
    {
      name: "jsx",
      type: {
        "preserve": JsxEmit.Preserve,
        "react": JsxEmit.React
      },
      paramType: Diagnostics.KIND,
      description: Diagnostics.Specify_JSX_code_generation_Colon_preserve_or_react,
      error: Diagnostics.Argument_for_jsx_must_be_preserve_or_react
    },
    {
      name: "reactNamespace",
      type: "String",
      description: Diagnostics.Specifies_the_object_invoked_for_createElement_and_spread_when_targeting_react_JSX_emit
    },
    {
      name: "listFiles",
      type: "Boolean",
    },
    {
      name: "locale",
      type: "String",
    },
    {
      name: "mapRoot",
      type: "String",
      isFilePath: true,
      description: Diagnostics.Specifies_the_location_where_debugger_should_locate_map_files_instead_of_generated_locations,
      paramType: Diagnostics.LOCATION,
    },
    {
      name: "module",
      shortName: "m",
      type: {
        "none": ModuleKind.None,
        "commonjs": ModuleKind.CommonJS,
        "amd": ModuleKind.AMD,
        "system": ModuleKind.System,
        "umd": ModuleKind.UMD,
        "es6": ModuleKind.ES6,
        "es2015": ModuleKind.ES2015,
      },
      description: Diagnostics.Specify_module_code_generation_Colon_commonjs_amd_system_umd_or_es2015,
      paramType: Diagnostics.KIND,
      error: Diagnostics.Argument_for_module_option_must_be_commonjs_amd_system_umd_es2015_or_none
    },
    {
      name: "newLine",
      type: {
        "crlf": NewLineKind.CarriageReturnLineFeed,
        "lf": NewLineKind.LineFeed
      },
      description: Diagnostics.Specifies_the_end_of_line_sequence_to_be_used_when_emitting_files_Colon_CRLF_dos_or_LF_unix,
      paramType: Diagnostics.NEWLINE,
      error: Diagnostics.Argument_for_newLine_option_must_be_CRLF_or_LF
    },
    {
      name: "noEmit",
      type: "Boolean",
      description: Diagnostics.Do_not_emit_outputs,
    },
    {
      name: "noEmitHelpers",
      type: "Boolean"
    },
    {
      name: "noEmitOnError",
      type: "Boolean",
      description: Diagnostics.Do_not_emit_outputs_if_any_errors_were_reported,
    },
    {
      name: "noImplicitAny",
      type: "Boolean",
      description: Diagnostics.Raise_error_on_expressions_and_declarations_with_an_implied_any_type,
    },
    {
      name: "noLib",
      type: "Boolean",
    },
    {
      name: "noResolve",
      type: "Boolean",
    },
    {
      name: "skipDefaultLibCheck",
      type: "Boolean",
    },
    {
      name: "out",
      type: "String",
      isFilePath: false, // This is intentionally broken to support compatability with existing tsconfig files
                 // for correct behaviour, please use outFile
      paramType: Diagnostics.FILE,
    },
    {
      name: "outFile",
      type: "String",
      isFilePath: true,
      description: Diagnostics.Concatenate_and_emit_output_to_single_file,
      paramType: Diagnostics.FILE,
    },
    {
      name: "outDir",
      type: "String",
      isFilePath: true,
      description: Diagnostics.Redirect_output_structure_to_the_directory,
      paramType: Diagnostics.DIRECTORY,
    },
    {
      name: "preserveConstEnums",
      type: "Boolean",
      description: Diagnostics.Do_not_erase_const_enum_declarations_in_generated_code
    },
    {
      name: "pretty",
      paramType: Diagnostics.KIND,
      description: Diagnostics.Stylize_errors_and_messages_using_color_and_context_experimental,
      type: "Boolean"
    },
    {
      name: "project",
      shortName: "p",
      type: "String",
      isFilePath: true,
      description: Diagnostics.Compile_the_project_in_the_given_directory,
      paramType: Diagnostics.DIRECTORY
    },
    {
      name: "removeComments",
      type: "Boolean",
      description: Diagnostics.Do_not_emit_comments_to_output,
    },
    {
      name: "rootDir",
      type: "String",
      isFilePath: true,
      description: Diagnostics.Specifies_the_root_directory_of_input_files_Use_to_control_the_output_directory_structure_with_outDir,
      paramType: Diagnostics.LOCATION,
    },
    {
      name: "isolatedModules",
      type: "Boolean",
    },
    {
      name: "sourceMap",
      type: "Boolean",
      description: Diagnostics.Generates_corresponding_map_file,
    },
    {
      name: "sourceRoot",
      type: "String",
      isFilePath: true,
      description: Diagnostics.Specifies_the_location_where_debugger_should_locate_TypeScript_files_instead_of_source_locations,
      paramType: Diagnostics.LOCATION,
    },
    {
      name: "suppressExcessPropertyErrors",
      type: "Boolean",
      description: Diagnostics.Suppress_excess_property_checks_for_object_literals,
      experimental: true
    },
    {
      name: "suppressImplicitAnyIndexErrors",
      type: "Boolean",
      description: Diagnostics.Suppress_noImplicitAny_errors_for_indexing_objects_lacking_index_signatures,
    },
    {
      name: "stripInternal",
      type: "Boolean",
      description: Diagnostics.Do_not_emit_declarations_for_code_that_has_an_internal_annotation,
      experimental: true
    },
    {
      name: "target",
      shortName: "t",
      type: {
        "es3": ScriptTarget.ES3,
        "es5": ScriptTarget.ES5,
        "es6": ScriptTarget.ES6,
        "es2015": ScriptTarget.ES2015,
      },
      description: Diagnostics.Specify_ECMAScript_target_version_Colon_ES3_default_ES5_or_ES2015_experimental,
      paramType: Diagnostics.VERSION,
      error: Diagnostics.Argument_for_target_option_must_be_ES3_ES5_or_ES2015
    },
    {
      name: "version",
      shortName: "v",
      type: "Boolean",
      description: Diagnostics.Print_the_compiler_s_version,
    },
    {
      name: "watch",
      shortName: "w",
      type: "Boolean",
      description: Diagnostics.Watch_input_files,
    },
    {
      name: "experimentalDecorators",
      type: "Boolean",
      description: Diagnostics.Enables_experimental_support_for_ES7_decorators
    },
    {
      name: "emitDecoratorMetadata",
      type: "Boolean",
      experimental: true,
      description: Diagnostics.Enables_experimental_support_for_emitting_type_metadata_for_decorators
    },
    {
      name: "moduleResolution",
      type: {
        "node": ModuleResolutionKind.NodeJs,
        "classic": ModuleResolutionKind.Classic,
      },
      description: Diagnostics.Specifies_module_resolution_strategy_Colon_node_Node_js_or_classic_TypeScript_pre_1_6,
      error: Diagnostics.Argument_for_moduleResolution_option_must_be_node_or_classic,
    },
    {
      name: "allowUnusedLabels",
      type: "Boolean",
      description: Diagnostics.Do_not_report_errors_on_unused_labels
    },
    {
      name: "noImplicitReturns",
      type: "Boolean",
      description: Diagnostics.Report_error_when_not_all_code_paths_in_function_return_a_value
    },
    {
      name: "noFallthroughCasesInSwitch",
      type: "Boolean",
      description: Diagnostics.Report_errors_for_fallthrough_cases_in_switch_statement
    },
    {
      name: "allowUnreachableCode",
      type: "Boolean",
      description: Diagnostics.Do_not_report_errors_on_unreachable_code
    },
    {
      name: "forceConsistentCasingInFileNames",
      type: "Boolean",
      description: Diagnostics.Disallow_inconsistently_cased_references_to_the_same_file
    },
    {
      name: "baseUrl",
      type: "String",
      isFilePath: true,
      description: Diagnostics.Base_directory_to_resolve_non_absolute_module_names
    },
    {
      // this option can only be specified in tsconfig.json
      // use type = object to copy the value as-is
      name: "paths",
      type: "object",
      isTSConfigOnly: true
    },
    {
      // this option can only be specified in tsconfig.json
      // use type = object to copy the value as-is
      name: "rootDirs",
      type: "object",
      isTSConfigOnly: true,
      isFilePath: true
    },
    {
      name: "traceModuleResolution",
      type: "Boolean",
      description: Diagnostics.Enable_tracing_of_the_module_resolution_process
    },
    {
      name: "allowJs",
      type: "Boolean",
      description: Diagnostics.Allow_javascript_files_to_be_compiled
    },
    {
      name: "allowSyntheticDefaultImports",
      type: "Boolean",
      description: Diagnostics.Allow_default_imports_from_modules_with_no_default_export_This_does_not_affect_code_emit_just_typechecking
    },
    {
      name: "noImplicitUseStrict",
      type: "Boolean",
      description: Diagnostics.Do_not_emit_use_strict_directives_in_module_output
    }
  ]

  /* @internal */
  interface OptionNameMap {
    optionNameMap: Map<CommandLineOption>
    shortOptionNames: Map<String>
  }

  var optionNameMapCache: OptionNameMap
  /* @internal */
  def getOptionNameMap(): OptionNameMap {
    if (optionNameMapCache) {
      return optionNameMapCache
    }

    val optionNameMap: Map<CommandLineOption> = {}
    val shortOptionNames: Map<String> = {}
    forEach(optionDeclarations, option => {
      optionNameMap[option.name.toLowerCase()] = option
      if (option.shortName) {
        shortOptionNames[option.shortName] = option.name
      }
    })

    optionNameMapCache = { optionNameMap, shortOptionNames }
    return optionNameMapCache
  }

  def parseCommandLine(commandLine: String[], readFile?: (path: String) => String): ParsedCommandLine {
    val options: CompilerOptions = {}
    val fileNames: String[] = []
    val errors: Diagnostic[] = []
    val { optionNameMap, shortOptionNames } = getOptionNameMap()

    parseStrings(commandLine)
    return {
      options,
      fileNames,
      errors
    }

    def parseStrings(args: String[]) {
      var i = 0
      while (i < args.length) {
        var s = args[i]
        i++
        if (s.charCodeAt(0) == CharacterCodes.at) {
          parseResponseFile(s.slice(1))
        }
        else if (s.charCodeAt(0) == CharacterCodes.minus) {
          s = s.slice(s.charCodeAt(1) == CharacterCodes.minus ? 2 : 1).toLowerCase()

          // Try to translate short option names to their full equivalents.
          if (hasProperty(shortOptionNames, s)) {
            s = shortOptionNames[s]
          }

          if (hasProperty(optionNameMap, s)) {
            val opt = optionNameMap[s]

            if (opt.isTSConfigOnly) {
              errors.push(createCompilerDiagnostic(Diagnostics.Option_0_can_only_be_specified_in_tsconfig_json_file, opt.name))
            }
            else {
              // Check to see if no argument was provided (e.g. "--locale" is the last command-line argument).
              if (!args[i] && opt.type != "Boolean") {
                errors.push(createCompilerDiagnostic(Diagnostics.Compiler_option_0_expects_an_argument, opt.name))
              }

              switch (opt.type) {
                case "Int":
                  options[opt.name] = parseInt(args[i])
                  i++
                  break
                case "Boolean":
                  options[opt.name] = true
                  break
                case "String":
                  options[opt.name] = args[i] || ""
                  i++
                  break
                // If not a primitive, the possible types are specified in what is effectively a map of options.
                default:
                  var map = <Map<Int>>opt.type
                  var key = (args[i] || "").toLowerCase()
                  i++
                  if (hasProperty(map, key)) {
                    options[opt.name] = map[key]
                  }
                  else {
                    errors.push(createCompilerDiagnostic((<CommandLineOptionOfCustomType>opt).error))
                  }
              }
            }
          }
          else {
            errors.push(createCompilerDiagnostic(Diagnostics.Unknown_compiler_option_0, s))
          }
        }
        else {
          fileNames.push(s)
        }
      }
    }

    def parseResponseFile(fileName: String) {
      val text = readFile ? readFile(fileName) : sys.readFile(fileName)

      if (!text) {
        errors.push(createCompilerDiagnostic(Diagnostics.File_0_not_found, fileName))
        return
      }

      val args: String[] = []
      var pos = 0
      while (true) {
        while (pos < text.length && text.charCodeAt(pos) <= CharacterCodes.space) pos++
        if (pos >= text.length) break
        val start = pos
        if (text.charCodeAt(start) == CharacterCodes.doubleQuote) {
          pos++
          while (pos < text.length && text.charCodeAt(pos) != CharacterCodes.doubleQuote) pos++
          if (pos < text.length) {
            args.push(text.substring(start + 1, pos))
            pos++
          }
          else {
            errors.push(createCompilerDiagnostic(Diagnostics.Unterminated_quoted_string_in_response_file_0, fileName))
          }
        }
        else {
          while (text.charCodeAt(pos) > CharacterCodes.space) pos++
          args.push(text.substring(start, pos))
        }
      }
      parseStrings(args)
    }
  }

  /**
    * Read tsconfig.json file
    * @param fileName The path to the config file
    */
  def readConfigFile(fileName: String, readFile: (path: String) => String): { config?: any; error?: Diagnostic }  {
    var text = ""
    try {
      text = readFile(fileName)
    }
    catch (e) {
      return { error: createCompilerDiagnostic(Diagnostics.Cannot_read_file_0_Colon_1, fileName, e.message) }
    }
    return parseConfigFileTextToJson(fileName, text)
  }

  /**
    * Parse the text of the tsconfig.json file
    * @param fileName The path to the config file
    * @param jsonText The text of the config file
    */
  def parseConfigFileTextToJson(fileName: String, jsonText: String): { config?: any; error?: Diagnostic } {
    try {
      val jsonTextWithoutComments = removeComments(jsonText)
      return { config: /\S/.test(jsonTextWithoutComments) ? JSON.parse(jsonTextWithoutComments) : {} }
    }
    catch (e) {
      return { error: createCompilerDiagnostic(Diagnostics.Failed_to_parse_file_0_Colon_1, fileName, e.message) }
    }
  }


  /**
   * Remove the comments from a json like text.
   * Comments can be single line comments (starting with # or //) or multiline comments using / * * /
   *
   * This method replace comment content by whitespace rather than completely remove them to keep positions in json parsing error reporting accurate.
   */
  def removeComments(jsonText: String): String {
    var output = ""
    val scanner = createScanner(ScriptTarget.ES5, /* skipTrivia */ false, LanguageVariant.Standard, jsonText)
    var token: SyntaxKind
    while ((token = scanner.scan()) != SyntaxKind.EndOfFileToken) {
      switch (token) {
        case SyntaxKind.SingleLineCommentTrivia:
        case SyntaxKind.MultiLineCommentTrivia:
          // replace comments with whitespace to preserve original character positions
          output += scanner.getTokenText().replace(/\S/g, " ")
          break
        default:
          output += scanner.getTokenText()
          break
      }
    }
    return output
  }

  /**
    * Parse the contents of a config file (tsconfig.json).
    * @param json The contents of the config file to parse
    * @param host Instance of ParseConfigHost used to enumerate files in folder.
    * @param basePath A root directory to resolve relative path entries in the config
    *  file to. e.g. outDir
    */
  def parseJsonConfigFileContent(json: any, host: ParseConfigHost, basePath: String, existingOptions: CompilerOptions = {}, configFileName?: String): ParsedCommandLine {
    val { options: optionsFromJsonConfigFile, errors } = convertCompilerOptionsFromJson(json["compilerOptions"], basePath, configFileName)

    val options = extend(existingOptions, optionsFromJsonConfigFile)

    return {
      options,
      fileNames: getFileNames(),
      errors
    }

    def getFileNames(): String[] {
      var fileNames: String[] = []
      if (hasProperty(json, "files")) {
        if (json["files"] instanceof Array) {
          fileNames = map(<String[]>json["files"], s => combinePaths(basePath, s))
        }
        else {
          errors.push(createCompilerDiagnostic(Diagnostics.Compiler_option_0_requires_a_value_of_type_1, "files", "Array"))
        }
      }
      else {
        val filesSeen: Map<Boolean> = {}

        var exclude: String[] = []
        if (json["exclude"] instanceof Array) {
          exclude = json["exclude"]
        }
        else {
          // by default exclude node_modules, and any specificied output directory
          exclude = ["node_modules"]
          val outDir = json["compilerOptions"] && json["compilerOptions"]["outDir"]
          if (outDir) {
            exclude.push(outDir)
          }
        }
        exclude = map(exclude, normalizeSlashes)

        val supportedExtensions = getSupportedExtensions(options)
        Debug.assert(indexOf(supportedExtensions, ".ts") < indexOf(supportedExtensions, ".d.ts"), "Changed priority of extensions to pick")

        // Get files of supported extensions in their order of resolution
        for (val extension of supportedExtensions) {
          val filesInDirWithExtension = host.readDirectory(basePath, extension, exclude)
          for (val fileName of filesInDirWithExtension) {
            // .ts extension would read the .d.ts extension files too but since .d.ts is lower priority extension,
            // lets pick them when its turn comes up
            if (extension == ".ts" && fileExtensionIs(fileName, ".d.ts")) {
              continue
            }

            // Skip over any minified JavaScript files (ending in ".min.js")
            if (/\.min\.js$/.test(fileName)) {
              continue
            }

            // If this is one of the output extension (which would be .d.ts and .js if we are allowing compilation of js files)
            // do not include this file if we included .ts or .tsx file with same base name as it could be output of the earlier compilation
            if (extension == ".d.ts" || (options.allowJs && contains(supportedJavascriptExtensions, extension))) {
              val baseName = fileName.substr(0, fileName.length - extension.length)
              if (hasProperty(filesSeen, baseName + ".ts") || hasProperty(filesSeen, baseName + ".tsx")) {
                continue
              }
            }

            filesSeen[fileName] = true
            fileNames.push(fileName)
          }
        }
      }
      return fileNames
    }
  }

  def convertCompilerOptionsFromJson(jsonOptions: any, basePath: String, configFileName?: String): { options: CompilerOptions, errors: Diagnostic[] } {
    val options: CompilerOptions = {}
    val errors: Diagnostic[] = []

    if (configFileName && getBaseFileName(configFileName) == "jsconfig.json") {
      options.allowJs = true
    }

    if (!jsonOptions) {
      return { options, errors }
    }

    val optionNameMap = arrayToMap(optionDeclarations, opt => opt.name)

    for (val id in jsonOptions) {
      if (hasProperty(optionNameMap, id)) {
        val opt = optionNameMap[id]
        val optType = opt.type
        var value = jsonOptions[id]
        val expectedType = typeof optType == "String" ? optType : "String"
        if (typeof value == expectedType) {
          if (typeof optType != "String") {
            val key = value.toLowerCase()
            if (hasProperty(optType, key)) {
              value = optType[key]
            }
            else {
              errors.push(createCompilerDiagnostic((<CommandLineOptionOfCustomType>opt).error))
              value = 0
            }
          }
          if (opt.isFilePath) {
            switch (typeof value) {
              case "String":
                value = normalizePath(combinePaths(basePath, value))
                break
              case "object":
                // "object" options with 'isFilePath' = true expected to be String arrays
                var paths: String[] = []
                var invalidOptionType = false
                if (!isArray(value)) {
                  invalidOptionType = true
                }
                else {
                  for (val element of <any[]>value) {
                    if (typeof element == "String") {
                      paths.push(normalizePath(combinePaths(basePath, element)))
                    }
                    else {
                      invalidOptionType = true
                      break
                    }
                  }
                }
                if (invalidOptionType) {
                  errors.push(createCompilerDiagnostic(Diagnostics.Option_0_should_have_array_of_strings_as_a_value, opt.name))
                }
                else {
                  value = paths
                }
                break
            }
            if (value == "") {
              value = "."
            }
          }
          options[opt.name] = value
        }
        else {
          errors.push(createCompilerDiagnostic(Diagnostics.Compiler_option_0_requires_a_value_of_type_1, id, expectedType))
        }
      }
      else {
        errors.push(createCompilerDiagnostic(Diagnostics.Unknown_compiler_option_0, id))
      }
    }

    return { options, errors }
  }
}
