import 'rest_request.dart';

/// Types of file renditions supported by the server.
enum RenditionType {
  flash,
  pdf,
  thumb120by90,
  thumb240by180,
  thumb720by480;

  /// Whether this rendition type produces a PNG image.
  bool get isPng =>
      this == thumb120by90 || this == thumb240by180 || this == thumb720by480;

  @override
  String toString() => name.toUpperCase();
}

/// Provides factory methods for Salesforce Chatter Files REST API requests.
///
/// Supports listing, viewing, sharing, and uploading files through
/// the Connect API.
class FileRequests {
  static const String _servicesData = RestRequest.servicesData;
  static const String _defaultApiVersion = '62.0';

  /// Returns the base path for ContentDocumentLink operations.
  static String getContentDocumentLinkPath({String apiVersion = _defaultApiVersion}) {
    return '$_servicesData/v$apiVersion/sobjects/ContentDocumentLink';
  }

  /// Builds a request to fetch files owned by the specified user.
  ///
  /// [userId] If null, the context user is used.
  /// [pageNum] If null, fetches the first page.
  static RestRequest ownedFilesList({
    String apiVersion = _defaultApiVersion,
    String? userId,
    int? pageNum,
  }) {
    var path = '$_servicesData/v$apiVersion/connect/files/users';
    path = _appendUserId(path, userId);
    path = _appendPageNum(path, pageNum);
    return RestRequest(method: RestMethod.get, path: path);
  }

  /// Builds a request to fetch files from groups that the user is a member of.
  ///
  /// [userId] If null, the context user is used.
  /// [pageNum] If null, fetches the first page.
  static RestRequest filesInUsersGroups({
    String apiVersion = _defaultApiVersion,
    String? userId,
    int? pageNum,
  }) {
    var path = '$_servicesData/v$apiVersion/connect/files/users';
    path = _appendUserId(path, userId);
    path += '/filter/groups';
    path = _appendPageNum(path, pageNum);
    return RestRequest(method: RestMethod.get, path: path);
  }

  /// Builds a request to fetch files shared with the specified user.
  ///
  /// [userId] If null, the context user is used.
  /// [pageNum] If null, fetches the first page.
  static RestRequest filesSharedWithUser({
    String apiVersion = _defaultApiVersion,
    String? userId,
    int? pageNum,
  }) {
    var path = '$_servicesData/v$apiVersion/connect/files/users';
    path = _appendUserId(path, userId);
    path += '/filter/sharedwithme';
    path = _appendPageNum(path, pageNum);
    return RestRequest(method: RestMethod.get, path: path);
  }

  /// Builds a request to fetch file details for a specific version.
  ///
  /// [sfdcId] The ID of the file.
  /// [version] If null, fetches the most recent version.
  static RestRequest fileDetails({
    required String apiVersion,
    required String sfdcId,
    String? version,
  }) {
    _validateSfdcId(sfdcId);
    var path = '$_servicesData/v$apiVersion/connect/files/$sfdcId';
    path = _appendVersionNum(path, version);
    return RestRequest(method: RestMethod.get, path: path);
  }

  /// Builds a request to fetch details of multiple files in a single call.
  ///
  /// [sfdcIds] The list of file IDs to fetch.
  static RestRequest batchFileDetails({
    String apiVersion = _defaultApiVersion,
    required List<String> sfdcIds,
  }) {
    _validateSfdcIds(sfdcIds);
    final ids = sfdcIds.join(',');
    return RestRequest(
      method: RestMethod.get,
      path: '$_servicesData/v$apiVersion/connect/files/batch/$ids',
    );
  }

  /// Builds a request to fetch a preview/rendition of a file page.
  ///
  /// [sfdcId] The ID of the file.
  /// [version] If null, fetches the most recent version.
  /// [renditionType] The format of rendition to get.
  /// [pageNum] Which page to fetch (pages start at 0).
  static RestRequest fileRendition({
    String apiVersion = _defaultApiVersion,
    required String sfdcId,
    String? version,
    required RenditionType renditionType,
    int? pageNum,
  }) {
    _validateSfdcId(sfdcId);
    var path =
        '$_servicesData/v$apiVersion/connect/files/$sfdcId/rendition?type=$renditionType';
    path = _appendVersionNum(path, version);
    path = _appendPageNum(path, pageNum);
    return RestRequest(method: RestMethod.get, path: path);
  }

  /// Builds a request to fetch the binary contents of a file.
  ///
  /// [sfdcId] The ID of the file.
  /// [version] The version of the file.
  static RestRequest fileContents({
    String apiVersion = _defaultApiVersion,
    required String sfdcId,
    String? version,
  }) {
    _validateSfdcId(sfdcId);
    var path = '$_servicesData/v$apiVersion/connect/files/$sfdcId/content';
    path = _appendVersionNum(path, version);
    return RestRequest(method: RestMethod.get, path: path);
  }

  /// Builds a request to fetch the entities a file is shared with.
  ///
  /// [sfdcId] The ID of the file.
  /// [pageNum] If null, fetches the first page.
  static RestRequest fileShares({
    String apiVersion = _defaultApiVersion,
    required String sfdcId,
    int? pageNum,
  }) {
    _validateSfdcId(sfdcId);
    var path =
        '$_servicesData/v$apiVersion/connect/files/$sfdcId/file-shares';
    path = _appendPageNum(path, pageNum);
    return RestRequest(method: RestMethod.get, path: path);
  }

  /// Builds a request to share a file with an entity.
  ///
  /// [fileId] The ID of the file being shared.
  /// [entityId] The ID of the entity to share with (user or group).
  /// [shareType] The type of share ('V' for View, 'C' for Collaboration).
  static RestRequest addFileShare({
    String apiVersion = _defaultApiVersion,
    required String fileId,
    required String entityId,
    required String shareType,
  }) {
    _validateSfdcId(fileId);
    _validateSfdcId(entityId);
    return RestRequest(
      method: RestMethod.post,
      path: getContentDocumentLinkPath(apiVersion: apiVersion),
      requestBody: {
        'ContentDocumentId': fileId,
        'LinkedEntityId': entityId,
        'ShareType': shareType,
      },
    );
  }

  /// Builds a request to delete a file share.
  ///
  /// [shareId] The ID of the file share record (ContentDocumentLink).
  static RestRequest deleteFileShare({
    String apiVersion = _defaultApiVersion,
    required String shareId,
  }) {
    _validateSfdcId(shareId);
    return RestRequest(
      method: RestMethod.delete,
      path: '${getContentDocumentLinkPath(apiVersion: apiVersion)}/$shareId',
    );
  }

  /// Builds a multipart request to upload a file.
  ///
  /// Note: In Flutter, file upload requires using a multipart HTTP request.
  /// This method builds the request metadata; the caller is responsible for
  /// attaching the file data as a multipart body using their HTTP client.
  ///
  /// [name] The file name.
  /// [title] The title of the file.
  /// [description] A description of the file.
  static RestRequest uploadFile({
    String apiVersion = _defaultApiVersion,
    required String name,
    String? title,
    String? description,
  }) {
    final body = <String, dynamic>{};
    if (title != null) body['title'] = title;
    if (description != null) body['desc'] = description;
    body['fileName'] = name;
    return RestRequest(
      method: RestMethod.post,
      path: '$_servicesData/v$apiVersion/connect/files/users/me',
      requestBody: body,
    );
  }

  static String _appendUserId(String path, String? userId) {
    return '$path/${userId ?? 'me'}';
  }

  static String _appendPageNum(String path, int? pageNum) {
    if (pageNum == null) return path;
    final separator = path.contains('?') ? '&' : '?';
    return '$path${separator}page=$pageNum';
  }

  static String _appendVersionNum(String path, String? version) {
    if (version == null) return path;
    final separator = path.contains('?') ? '&' : '?';
    return '$path${separator}versionNumber=$version';
  }

  static void _validateSfdcId(String id) {
    if (id.isEmpty) {
      throw ArgumentError('Salesforce ID cannot be empty');
    }
  }

  static void _validateSfdcIds(List<String> ids) {
    if (ids.isEmpty) {
      throw ArgumentError('Salesforce ID list cannot be empty');
    }
    for (final id in ids) {
      _validateSfdcId(id);
    }
  }
}
