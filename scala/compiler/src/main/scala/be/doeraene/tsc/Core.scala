package be.doeraene.tsc

/// <reference path="types.ts"/>

/* @internal */
object Core {
  /**
   * Ternary values are defined such that
   * x & y is False if either x or y is False.
   * x & y is Maybe if either x or y is Maybe, but neither x or y is False.
   * x & y is True if both x and y are True.
   * x | y is False if both x and y are False.
   * x | y is Maybe if either x or y is Maybe, but neither x or y is True.
   * x | y is True if either x or y is True.
   */
  val enum Ternary {
    False = 0,
    Maybe = 1,
    True = -1
  }

  def createFileMap<T>(keyMapper?: (key: String) => String): FileMap<T> {
    var files: Map<T> = {}
    return {
      get,
      set,
      contains,
      remove,
      forEachValue: forEachValueInMap,
      clear
    }

    def forEachValueInMap(f: (key: Path, value: T) => Unit) = {
      for (val key in files) {
        f(<Path>key, files[key])
      }
    }

    // path should already be well-formed so it does not need to be normalized
    def get(path: Path): T = {
      return files[toKey(path)]
    }

    def set(path: Path, value: T) = {
      files[toKey(path)] = value
    }

    def contains(path: Path) = {
      return hasProperty(files, toKey(path))
    }

    def remove(path: Path) = {
      val key = toKey(path)
      delete files[key]
    }

    def clear() = {
      files = {}
    }

    def toKey(path: Path): String = {
      return keyMapper ? keyMapper(path) : path
    }
  }

  def toPath(fileName: String, basePath: String, getCanonicalFileName: (path: String) => String): Path = {
    val nonCanonicalizedPath = isRootedDiskPath(fileName)
      ? normalizePath(fileName)
      : getNormalizedAbsolutePath(fileName, basePath)
    return <Path>getCanonicalFileName(nonCanonicalizedPath)
  }

  val enum Comparison {
    LessThan  = -1,
    EqualTo   = 0,
    GreaterThan = 1
  }

  /**
   * Iterates through 'array' by index and performs the callback on each element of array until the callback
   * returns a truthy value, then returns that value.
   * If no such value is found, the callback is applied to each element of array and () is returned.
   */
  def forEach<T, U>(array: Array[T], callback: (element: T, index: Int) => U): U = {
    if (array) {
      for (var i = 0, len = array.length; i < len; i++) {
        val result = callback(array[i], i)
        if (result) {
          return result
        }
      }
    }
    return ()
  }

  def contains<T>(array: Array[T], value: T): Boolean = {
    if (array) {
      for (val v of array) {
        if (v == value) {
          return true
        }
      }
    }
    return false
  }

  def indexOf<T>(array: Array[T], value: T): Int = {
    if (array) {
      for (var i = 0, len = array.length; i < len; i++) {
        if (array[i] == value) {
          return i
        }
      }
    }
    return -1
  }

  def countWhere<T>(array: Array[T], predicate: (x: T) => Boolean): Int = {
    var count = 0
    if (array) {
      for (val v of array) {
        if (predicate(v)) {
          count++
        }
      }
    }
    return count
  }

  def filter<T>(array: Array[T], f: (x: T) => Boolean): Array[T] {
    var result: Array[T]
    if (array) {
      result = []
      for (val item of array) {
        if (f(item)) {
          result.push(item)
        }
      }
    }
    return result
  }

  def map<T, U>(array: Array[T], f: (x: T) => U): Array[U] {
    var result: Array[U]
    if (array) {
      result = []
      for (val v of array) {
        result.push(f(v))
      }
    }
    return result
  }

  def concatenate<T>(array1: Array[T], array2: Array[T]): Array[T] {
    if (!array2 || !array2.length) return array1
    if (!array1 || !array1.length) return array2

    return array1.concat(array2)
  }

  def deduplicate<T>(array: Array[T]): Array[T] {
    var result: Array[T]
    if (array) {
      result = []
      for (val item of array) {
        if (!contains(result, item)) {
          result.push(item)
        }
      }
    }
    return result
  }

  def sum(array: Array[any], prop: String): Int = {
    var result = 0
    for (val v of array) {
      result += v[prop]
    }
    return result
  }

  def addRange<T>(to: Array[T], from: Array[T]): Unit = {
    if (to && from) {
      for (val v of from) {
        to.push(v)
      }
    }
  }

  def rangeEquals<T>(array1: Array[T], array2: Array[T], pos: Int, end: Int) = {
    while (pos < end) {
      if (array1[pos] != array2[pos]) {
        return false
      }
      pos++
    }
    return true
  }

  /**
   * Returns the last element of an array if non-empty, () otherwise.
   */
  def lastOrUndefined<T>(array: Array[T]): T = {
    if (array.length == 0) {
      return ()
    }

    return array[array.length - 1]
  }

  /**
   * Performs a binary search, finding the index at which 'value' occurs in 'array'.
   * If no such index is found, returns the 2's-complement of first index at which
   * Int[index] exceeds Int.
   * @param array A sorted array whose first element must be no larger than Int
   * @param Int The value to be searched for in the array.
   */
  def binarySearch(array: Array[Int], value: Int): Int = {
    var low = 0
    var high = array.length - 1

    while (low <= high) {
      val middle = low + ((high - low) >> 1)
      val midValue = array[middle]

      if (midValue == value) {
        return middle
      }
      else if (midValue > value) {
        high = middle - 1
      }
      else {
        low = middle + 1
      }
    }

    return ~low
  }

  def reduceLeft<T>(array: Array[T], f: (a: T, x: T) => T): T
  def reduceLeft<T, U>(array: Array[T], f: (a: U, x: T) => U, initial: U): U
  def reduceLeft<T, U>(array: Array[T], f: (a: U, x: T) => U, initial?: U): U = {
    if (array) {
      val count = array.length
      if (count > 0) {
        var pos = 0
        var result = arguments.length <= 2 ? array[pos] : initial
        pos++
        while (pos < count) {
          result = f(<U>result, array[pos])
          pos++
        }
        return <U>result
      }
    }
    return initial
  }

  def reduceRight<T>(array: Array[T], f: (a: T, x: T) => T): T
  def reduceRight<T, U>(array: Array[T], f: (a: U, x: T) => U, initial: U): U
  def reduceRight<T, U>(array: Array[T], f: (a: U, x: T) => U, initial?: U): U = {
    if (array) {
      var pos = array.length - 1
      if (pos >= 0) {
        var result = arguments.length <= 2 ? array[pos] : initial
        pos--
        while (pos >= 0) {
          result = f(<U>result, array[pos])
          pos--
        }
        return <U>result
      }
    }
    return initial
  }

  val hasOwnProperty = Object.prototype.hasOwnProperty

  def hasProperty<T>(map: Map<T>, key: String): Boolean = {
    return hasOwnProperty.call(map, key)
  }

  def getProperty<T>(map: Map<T>, key: String): T = {
    return hasOwnProperty.call(map, key) ? map[key] : ()
  }

  def isEmpty<T>(map: Map<T>) = {
    for (val id in map) {
      if (hasProperty(map, id)) {
        return false
      }
    }
    return true
  }

  def clone<T>(object: T): T = {
    val result: any = {}
    for (val id in object) {
      result[id] = (<any>object)[id]
    }
    return <T>result
  }

  def extend<T1 extends Map<{}>, T2 extends Map<{}>>(first: T1 , second: T2): T1 & T2 {
    val result: T1 & T2 = <any>{}
    for (val id in first) {
      (result as any)[id] = first[id]
    }
    for (val id in second) {
      if (!hasProperty(result, id)) {
        (result as any)[id] = second[id]
      }
    }
    return result
  }

  def forEachValue<T, U>(map: Map<T>, callback: (value: T) => U): U = {
    var result: U
    for (val id in map) {
      if (result = callback(map[id])) break
    }
    return result
  }

  def forEachKey<T, U>(map: Map<T>, callback: (key: String) => U): U = {
    var result: U
    for (val id in map) {
      if (result = callback(id)) break
    }
    return result
  }

  def lookUp<T>(map: Map<T>, key: String): T = {
    return hasProperty(map, key) ? map[key] : ()
  }

  def copyMap<T>(source: Map<T>, target: Map<T>): Unit = {
    for (val p in source) {
      target[p] = source[p]
    }
  }

  /**
   * Creates a map from the elements of an array.
   *
   * @param array the array of input elements.
   * @param makeKey a def that produces a key for a given element.
   *
   * This def makes no effort to avoid collisions; if any two elements produce
   * the same key with the given 'makeKey' def, then the element with the higher
   * index in the array will be the one associated with the produced key.
   */
  def arrayToMap<T>(array: Array[T], makeKey: (value: T) => String): Map<T> {
    val result: Map<T> = {}

    forEach(array, value => {
      result[makeKey(value)] = value
    })

    return result
  }

  /**
   * Reduce the properties of a map.
   *
   * @param map The map to reduce
   * @param callback An aggregation def that is called for each entry in the map
   * @param initial The initial value for the reduction.
   */
  def reduceProperties<T, U>(map: Map<T>, callback: (aggregate: U, value: T, key: String) => U, initial: U): U = {
    var result = initial
    if (map) {
      for (val key in map) {
        if (hasProperty(map, key)) {
          result = callback(result, map[key], String(key))
        }
      }
    }

    return result
  }

  /**
   * Tests whether a value is an array.
   */
  def isArray(value: any): value is Array[any] {
    return Array.isArray ? Array.isArray(value) : value instanceof Array
  }

  def memoize<T>(callback: () => T): () => T {
    var value: T
    return () => {
      if (callback) {
        value = callback()
        callback = ()
      }
      return value
    }
  }

  def formatStringFromArgs(text: String, args: { [index: Int]: any; }, baseIndex?: Int): String = {
    baseIndex = baseIndex || 0

    return text.replace(/{(\d+)}/g, (match, index?) => args[+index + baseIndex])
  }

  var localizedDiagnosticMessages: Map<String> = ()

  def getLocaleSpecificMessage(message: DiagnosticMessage) = {
    return localizedDiagnosticMessages && localizedDiagnosticMessages[message.key]
      ? localizedDiagnosticMessages[message.key]
      : message.message
  }

  def createFileDiagnostic(file: SourceFile, start: Int, length: Int, message: DiagnosticMessage, ...args: Array[any]): Diagnostic
  def createFileDiagnostic(file: SourceFile, start: Int, length: Int, message: DiagnosticMessage): Diagnostic = {
    val end = start + length

    Debug.assert(start >= 0, "start must be non-negative, is " + start)
    Debug.assert(length >= 0, "length must be non-negative, is " + length)

    if (file) {
      Debug.assert(start <= file.text.length, `start must be within the bounds of the file. ${ start } > ${ file.text.length }`)
      Debug.assert(end <= file.text.length, `end must be the bounds of the file. ${ end } > ${ file.text.length }`)
    }

    var text = getLocaleSpecificMessage(message)

    if (arguments.length > 4) {
      text = formatStringFromArgs(text, arguments, 4)
    }

    return {
      file,
      start,
      length,

      messageText: text,
      category: message.category,
      code: message.code,
    }
  }

  /* internal */
  def formatMessage(dummy: any, message: DiagnosticMessage): String = {
    var text = getLocaleSpecificMessage(message)

    if (arguments.length > 2) {
      text = formatStringFromArgs(text, arguments, 2)
    }

    return text
  }

  def createCompilerDiagnostic(message: DiagnosticMessage, ...args: Array[any]): Diagnostic
  def createCompilerDiagnostic(message: DiagnosticMessage): Diagnostic = {
    var text = getLocaleSpecificMessage(message)

    if (arguments.length > 1) {
      text = formatStringFromArgs(text, arguments, 1)
    }

    return {
      file: (),
      start: (),
      length: (),

      messageText: text,
      category: message.category,
      code: message.code
    }
  }

  def chainDiagnosticMessages(details: DiagnosticMessageChain, message: DiagnosticMessage, ...args: Array[any]): DiagnosticMessageChain
  def chainDiagnosticMessages(details: DiagnosticMessageChain, message: DiagnosticMessage): DiagnosticMessageChain = {
    var text = getLocaleSpecificMessage(message)

    if (arguments.length > 2) {
      text = formatStringFromArgs(text, arguments, 2)
    }

    return {
      messageText: text,
      category: message.category,
      code: message.code,

      next: details
    }
  }

  def concatenateDiagnosticMessageChains(headChain: DiagnosticMessageChain, tailChain: DiagnosticMessageChain): DiagnosticMessageChain = {
    var lastChain = headChain
    while (lastChain.next) {
      lastChain = lastChain.next
    }

    lastChain.next = tailChain
    return headChain
  }

  def compareValues<T>(a: T, b: T): Comparison = {
    if (a == b) return Comparison.EqualTo
    if (a == ()) return Comparison.LessThan
    if (b == ()) return Comparison.GreaterThan
    return a < b ? Comparison.LessThan : Comparison.GreaterThan
  }

  def getDiagnosticFileName(diagnostic: Diagnostic): String = {
    return diagnostic.file ? diagnostic.file.fileName : ()
  }

  def compareDiagnostics(d1: Diagnostic, d2: Diagnostic): Comparison = {
    return compareValues(getDiagnosticFileName(d1), getDiagnosticFileName(d2)) ||
      compareValues(d1.start, d2.start) ||
      compareValues(d1.length, d2.length) ||
      compareValues(d1.code, d2.code) ||
      compareMessageText(d1.messageText, d2.messageText) ||
      Comparison.EqualTo
  }

  def compareMessageText(text1: String | DiagnosticMessageChain, text2: String | DiagnosticMessageChain): Comparison = {
    while (text1 && text2) {
      // We still have both chains.
      val string1 = typeof text1 == "String" ? text1 : text1.messageText
      val string2 = typeof text2 == "String" ? text2 : text2.messageText

      val res = compareValues(string1, string2)
      if (res) {
        return res
      }

      text1 = typeof text1 == "String" ? () : text1.next
      text2 = typeof text2 == "String" ? () : text2.next
    }

    if (!text1 && !text2) {
      // if the chains are done, then these messages are the same.
      return Comparison.EqualTo
    }

    // We still have one chain remaining.  The shorter chain should come first.
    return text1 ? Comparison.GreaterThan : Comparison.LessThan
  }

  def sortAndDeduplicateDiagnostics(diagnostics: Array[Diagnostic]): Array[Diagnostic] {
    return deduplicateSortedDiagnostics(diagnostics.sort(compareDiagnostics))
  }

  def deduplicateSortedDiagnostics(diagnostics: Array[Diagnostic]): Array[Diagnostic] {
    if (diagnostics.length < 2) {
      return diagnostics
    }

    val newDiagnostics = [diagnostics[0]]
    var previousDiagnostic = diagnostics[0]
    for (var i = 1; i < diagnostics.length; i++) {
      val currentDiagnostic = diagnostics[i]
      val isDupe = compareDiagnostics(currentDiagnostic, previousDiagnostic) == Comparison.EqualTo
      if (!isDupe) {
        newDiagnostics.push(currentDiagnostic)
        previousDiagnostic = currentDiagnostic
      }
    }

    return newDiagnostics
  }

  def normalizeSlashes(path: String): String = {
    return path.replace(/\\/g, "/")
  }

  // Returns length of path root (i.e. length of "/", "x:/", "//server/share/, file:///user/files")
  def getRootLength(path: String): Int = {
    if (path.charCodeAt(0) == CharacterCodes.slash) {
      if (path.charCodeAt(1) != CharacterCodes.slash) return 1
      val p1 = path.indexOf("/", 2)
      if (p1 < 0) return 2
      val p2 = path.indexOf("/", p1 + 1)
      if (p2 < 0) return p1 + 1
      return p2 + 1
    }
    if (path.charCodeAt(1) == CharacterCodes.colon) {
      if (path.charCodeAt(2) == CharacterCodes.slash) return 3
      return 2
    }
    // Per RFC 1738 'file' URI schema has the shape file://<host>/<path>
    // if <host> is omitted then it is assumed that host value is 'localhost',
    // however slash after the omitted <host> is not removed.
    // file:///folder1/file1 - this is a correct URI
    // file://folder2/file2 - this is an incorrect URI
    if (path.lastIndexOf("file:///", 0) == 0) {
      return "file:///".length
    }
    val idx = path.indexOf("://")
    if (idx != -1) {
      return idx + "://".length
    }
    return 0
  }

  var directorySeparator = "/"
  def getNormalizedParts(normalizedSlashedPath: String, rootLength: Int) = {
    val parts = normalizedSlashedPath.substr(rootLength).split(directorySeparator)
    val normalized: Array[String] = []
    for (val part of parts) {
      if (part != ".") {
        if (part == ".." && normalized.length > 0 && lastOrUndefined(normalized) != "..") {
          normalized.pop()
        }
        else {
          // A part may be an empty String (which is 'falsy') if the path had consecutive slashes,
          // e.g. "path//file.ts".  Drop these before re-joining the parts.
          if (part) {
            normalized.push(part)
          }
        }
      }
    }

    return normalized
  }

  def normalizePath(path: String): String = {
    path = normalizeSlashes(path)
    val rootLength = getRootLength(path)
    val normalized = getNormalizedParts(path, rootLength)
    return path.substr(0, rootLength) + normalized.join(directorySeparator)
  }

  def getDirectoryPath(path: Path): Path
  def getDirectoryPath(path: String): String
  def getDirectoryPath(path: String): any = {
    return path.substr(0, Math.max(getRootLength(path), path.lastIndexOf(directorySeparator)))
  }

  def isUrl(path: String) = {
    return path && !isRootedDiskPath(path) && path.indexOf("://") != -1
  }

  def isRootedDiskPath(path: String) = {
    return getRootLength(path) != 0
  }

  def normalizedPathComponents(path: String, rootLength: Int) = {
    val normalizedParts = getNormalizedParts(path, rootLength)
    return [path.substr(0, rootLength)].concat(normalizedParts)
  }

  def getNormalizedPathComponents(path: String, currentDirectory: String) = {
    path = normalizeSlashes(path)
    var rootLength = getRootLength(path)
    if (rootLength == 0) {
      // If the path is not rooted it is relative to current directory
      path = combinePaths(normalizeSlashes(currentDirectory), path)
      rootLength = getRootLength(path)
    }

    return normalizedPathComponents(path, rootLength)
  }

  def getNormalizedAbsolutePath(fileName: String, currentDirectory: String) = {
    return getNormalizedPathFromPathComponents(getNormalizedPathComponents(fileName, currentDirectory))
  }

  def getNormalizedPathFromPathComponents(pathComponents: Array[String]) = {
    if (pathComponents && pathComponents.length) {
      return pathComponents[0] + pathComponents.slice(1).join(directorySeparator)
    }
  }

  def getNormalizedPathComponentsOfUrl(url: String) = {
    // Get root length of http://www.website.com/folder1/folder2/
    // In this example the root is:  http://www.website.com/
    // normalized path components should be ["http://www.website.com/", "folder1", "folder2"]

    val urlLength = url.length
    // Initial root length is http:// part
    var rootLength = url.indexOf("://") + "://".length
    while (rootLength < urlLength) {
      // Consume all immediate slashes in the protocol
      // eg.initial rootlength is just file:// but it needs to consume another "/" in file:///
      if (url.charCodeAt(rootLength) == CharacterCodes.slash) {
        rootLength++
      }
      else {
        // non slash character means we continue proceeding to next component of root search
        break
      }
    }

    // there are no parts after http:// just return current String as the pathComponent
    if (rootLength == urlLength) {
      return [url]
    }

    // Find the index of "/" after website.com so the root can be http://www.website.com/ (from existing http://)
    val indexOfNextSlash = url.indexOf(directorySeparator, rootLength)
    if (indexOfNextSlash != -1) {
      // Found the "/" after the website.com so the root is length of http://www.website.com/
      // and get components after the root normally like any other folder components
      rootLength = indexOfNextSlash + 1
      return normalizedPathComponents(url, rootLength)
    }
    else {
      // Can't find the host assume the rest of the String as component
      // but make sure we append "/"  to it as root is not joined using "/"
      // eg. if url passed in was http://website.com we want to use root as [http://website.com/]
      // so that other path manipulations will be correct and it can be merged with relative paths correctly
      return [url + directorySeparator]
    }
  }

  def getNormalizedPathOrUrlComponents(pathOrUrl: String, currentDirectory: String) = {
    if (isUrl(pathOrUrl)) {
      return getNormalizedPathComponentsOfUrl(pathOrUrl)
    }
    else {
      return getNormalizedPathComponents(pathOrUrl, currentDirectory)
    }
  }

  def getRelativePathToDirectoryOrUrl(directoryPathOrUrl: String, relativeOrAbsolutePath: String, currentDirectory: String, getCanonicalFileName: (fileName: String) => String, isAbsolutePathAnUrl: Boolean) = {
    val pathComponents = getNormalizedPathOrUrlComponents(relativeOrAbsolutePath, currentDirectory)
    val directoryComponents = getNormalizedPathOrUrlComponents(directoryPathOrUrl, currentDirectory)
    if (directoryComponents.length > 1 && lastOrUndefined(directoryComponents) == "") {
      // If the directory path given was of type test/cases/ then we really need components of directory to be only till its name
      // that is  ["test", "cases", ""] needs to be actually ["test", "cases"]
      directoryComponents.length--
    }

    // Find the component that differs
    var joinStartIndex: Int
    for (joinStartIndex = 0; joinStartIndex < pathComponents.length && joinStartIndex < directoryComponents.length; joinStartIndex++) {
      if (getCanonicalFileName(directoryComponents[joinStartIndex]) != getCanonicalFileName(pathComponents[joinStartIndex])) {
        break
      }
    }

    // Get the relative path
    if (joinStartIndex) {
      var relativePath = ""
      val relativePathComponents = pathComponents.slice(joinStartIndex, pathComponents.length)
      for (; joinStartIndex < directoryComponents.length; joinStartIndex++) {
        if (directoryComponents[joinStartIndex] != "") {
          relativePath = relativePath + ".." + directorySeparator
        }
      }

      return relativePath + relativePathComponents.join(directorySeparator)
    }

    // Cant find the relative path, get the absolute path
    var absolutePath = getNormalizedPathFromPathComponents(pathComponents)
    if (isAbsolutePathAnUrl && isRootedDiskPath(absolutePath)) {
      absolutePath = "file:///" + absolutePath
    }

    return absolutePath
  }

  def getBaseFileName(path: String) = {
    if (path == ()) {
      return ()
    }
    val i = path.lastIndexOf(directorySeparator)
    return i < 0 ? path : path.substring(i + 1)
  }

  def combinePaths(path1: String, path2: String) = {
    if (!(path1 && path1.length)) return path2
    if (!(path2 && path2.length)) return path1
    if (getRootLength(path2) != 0) return path2
    if (path1.charAt(path1.length - 1) == directorySeparator) return path1 + path2
    return path1 + directorySeparator + path2
  }

  def fileExtensionIs(path: String, extension: String): Boolean = {
    val pathLen = path.length
    val extLen = extension.length
    return pathLen > extLen && path.substr(pathLen - extLen, extLen) == extension
  }

  /**
   *  List of supported extensions in order of file resolution precedence.
   */
  val supportedTypeScriptExtensions = [".ts", ".tsx", ".d.ts"]
  val supportedJavascriptExtensions = [".js", ".jsx"]
  val allSupportedExtensions  = supportedTypeScriptExtensions.concat(supportedJavascriptExtensions)

  def getSupportedExtensions(options?: CompilerOptions): Array[String] {
    return options && options.allowJs ? allSupportedExtensions : supportedTypeScriptExtensions
  }

  def isSupportedSourceFileName(fileName: String, compilerOptions?: CompilerOptions) = {
    if (!fileName) { return false; }

    for (val extension of getSupportedExtensions(compilerOptions)) {
      if (fileExtensionIs(fileName, extension)) {
        return true
      }
    }
    return false
  }

  val extensionsToRemove = [".d.ts", ".ts", ".js", ".tsx", ".jsx"]
  def removeFileExtension(path: String): String = {
    for (val ext of extensionsToRemove) {
      if (fileExtensionIs(path, ext)) {
        return path.substr(0, path.length - ext.length)
      }
    }
    return path
  }

  trait ObjectAllocator {
    getNodeConstructor(): new (kind: SyntaxKind, pos?: Int, end?: Int) => Node
    getSourceFileConstructor(): new (kind: SyntaxKind, pos?: Int, end?: Int) => SourceFile
    getSymbolConstructor(): new (flags: SymbolFlags, name: String) => Symbol
    getTypeConstructor(): new (checker: TypeChecker, flags: TypeFlags) => Type
    getSignatureConstructor(): new (checker: TypeChecker) => Signature
  }

  def Symbol(flags: SymbolFlags, name: String) = {
    this.flags = flags
    this.name = name
    this.declarations = ()
  }

  def Type(checker: TypeChecker, flags: TypeFlags) = {
    this.flags = flags
  }

  def Signature(checker: TypeChecker) = {
  }

  def Node(kind: SyntaxKind, pos: Int, end: Int) = {
    this.kind = kind
    this.pos = pos
    this.end = end
    this.flags = NodeFlags.None
    this.parent = ()
  }

  var objectAllocator: ObjectAllocator = {
    getNodeConstructor: () => <any>Node,
    getSourceFileConstructor: () => <any>Node,
    getSymbolConstructor: () => <any>Symbol,
    getTypeConstructor: () => <any>Type,
    getSignatureConstructor: () => <any>Signature
  }

  val enum AssertionLevel {
    None = 0,
    Normal = 1,
    Aggressive = 2,
    VeryAggressive = 3,
  }

  package Debug {
    val currentAssertionLevel = AssertionLevel.None

    def shouldAssert(level: AssertionLevel): Boolean = {
      return currentAssertionLevel >= level
    }

    def assert(expression: Boolean, message?: String, verboseDebugInfo?: () => String): Unit = {
      if (!expression) {
        var verboseDebugString = ""
        if (verboseDebugInfo) {
          verboseDebugString = "\r\nVerbose Debug Information: " + verboseDebugInfo()
        }
        debugger
        throw new Error("Debug Failure. False expression: " + (message || "") + verboseDebugString)
      }
    }

    def fail(message?: String): Unit = {
      Debug.assert(/*expression*/ false, message)
    }
  }

  def copyListRemovingItem<T>(item: T, list: Array[T]) = {
    val copiedList: Array[T] = []
    for (val e of list) {
      if (e != item) {
        copiedList.push(e)
      }
    }
    return copiedList
  }

  def createGetCanonicalFileName(useCaseSensitivefileNames: Boolean): (fileName: String) => String {
    return useCaseSensitivefileNames
      ? ((fileName) => fileName)
      : ((fileName) => fileName.toLowerCase())
  }

}
