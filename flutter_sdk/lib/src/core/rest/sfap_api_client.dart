import 'dart:convert';
import 'rest_client.dart';
import 'rest_request.dart';
import 'rest_response.dart';

/// Exception for SFAP (Einstein Platform) API errors.
class SfapApiException implements Exception {
  final String? errorCode;
  final String? message;
  final String? messageCode;
  final String? source;

  SfapApiException({
    this.errorCode,
    this.message,
    this.messageCode,
    this.source,
  });

  factory SfapApiException.fromResponse(RestResponse response) {
    try {
      final json = jsonDecode(response.asString) as Map<String, dynamic>;
      return SfapApiException(
        errorCode: json['errorCode'] as String?,
        message: json['message'] as String?,
        messageCode: json['messageCode'] as String?,
        source: response.asString,
      );
    } catch (_) {
      return SfapApiException(
        message: 'HTTP ${response.statusCode}',
        source: response.asString,
      );
    }
  }

  @override
  String toString() => 'SfapApiException: $message ($errorCode)';
}

/// Chat message for SFAP chat generations.
class SfapChatMessage {
  final String role;
  final String content;

  const SfapChatMessage({required this.role, required this.content});

  Map<String, dynamic> toJson() => {'role': role, 'content': content};

  factory SfapChatMessage.fromJson(Map<String, dynamic> json) {
    return SfapChatMessage(
      role: json['role'] as String,
      content: json['content'] as String,
    );
  }
}

/// Request body for embeddings generation.
class SfapEmbeddingsRequestBody {
  final List<String> input;

  const SfapEmbeddingsRequestBody({required this.input});

  Map<String, dynamic> toJson() => {'input': input};
}

/// Response from embeddings generation.
class SfapEmbeddingsResponseBody {
  final List<Map<String, dynamic>>? data;
  final Map<String, dynamic>? usage;

  SfapEmbeddingsResponseBody({this.data, this.usage});

  factory SfapEmbeddingsResponseBody.fromJson(Map<String, dynamic> json) {
    return SfapEmbeddingsResponseBody(
      data: (json['data'] as List<dynamic>?)
          ?.cast<Map<String, dynamic>>(),
      usage: json['usage'] as Map<String, dynamic>?,
    );
  }
}

/// Request body for chat generations.
class SfapChatGenerationsRequestBody {
  final List<SfapChatMessage> messages;
  final Map<String, dynamic>? parameters;

  const SfapChatGenerationsRequestBody({
    required this.messages,
    this.parameters,
  });

  Map<String, dynamic> toJson() => {
        'messages': messages.map((m) => m.toJson()).toList(),
        if (parameters != null) ...parameters!,
      };
}

/// Response from chat generations.
class SfapChatGenerationsResponseBody {
  final String? id;
  final String? generationDetails;
  final Map<String, dynamic>? raw;

  SfapChatGenerationsResponseBody({
    this.id,
    this.generationDetails,
    this.raw,
  });

  factory SfapChatGenerationsResponseBody.fromJson(
      Map<String, dynamic> json) {
    return SfapChatGenerationsResponseBody(
      id: json['id'] as String?,
      generationDetails: json['generationDetails'] as String?,
      raw: json,
    );
  }
}

/// Response from text generations.
class SfapGenerationsResponseBody {
  final String? id;
  final Map<String, dynamic>? generation;
  final Map<String, dynamic>? raw;

  SfapGenerationsResponseBody({this.id, this.generation, this.raw});

  factory SfapGenerationsResponseBody.fromJson(Map<String, dynamic> json) {
    return SfapGenerationsResponseBody(
      id: json['id'] as String?,
      generation: json['generation'] as Map<String, dynamic>?,
      raw: json,
    );
  }
}

/// Request body for feedback submission.
class SfapFeedbackRequestBody {
  final String generationId;
  final String feedback;
  final String? feedbackText;
  final String? source;
  final String? appFeedback;

  const SfapFeedbackRequestBody({
    required this.generationId,
    required this.feedback,
    this.feedbackText,
    this.source,
    this.appFeedback,
  });

  Map<String, dynamic> toJson() => {
        'generationId': generationId,
        'feedback': feedback,
        if (feedbackText != null) 'feedbackText': feedbackText,
        if (source != null) 'source': source,
        if (appFeedback != null) 'appFeedback': appFeedback,
      };
}

/// Response from feedback submission.
class SfapFeedbackResponseBody {
  final String? id;
  final Map<String, dynamic>? raw;

  SfapFeedbackResponseBody({this.id, this.raw});

  factory SfapFeedbackResponseBody.fromJson(Map<String, dynamic> json) {
    return SfapFeedbackResponseBody(
      id: json['id'] as String?,
      raw: json,
    );
  }
}

/// REST client for Salesforce Einstein Platform (SFAP) API.
///
/// Supports chat generations, text generations, embeddings, and feedback.
/// Mirrors Android SfapApiClient for feature parity.
class SfapApiClient {
  final String apiHostName;
  final String? modelName;
  final RestClient _restClient;

  static const String _basePath = '/einstein/platform/v1';

  SfapApiClient({
    required this.apiHostName,
    this.modelName,
    required RestClient restClient,
  }) : _restClient = restClient;

  /// Standard SFAP API headers.
  static Map<String, String> generateHeaders() => {
        'x-sfdc-app-context': 'EinsteinGPT',
        'x-client-feature-id': 'ai-platform-models-connected-app',
      };

  /// Generates embeddings for the given input texts.
  ///
  /// Requires [modelName] to be set.
  Future<SfapEmbeddingsResponseBody> fetchGeneratedEmbeddings(
      SfapEmbeddingsRequestBody requestBody) async {
    _requireModelName();
    final request = _buildRequest(
      '/models/$modelName/embeddings',
      requestBody.toJson(),
    );
    final response = await _restClient.sendAsync(request);
    if (!response.isSuccess) throw SfapApiException.fromResponse(response);
    return SfapEmbeddingsResponseBody.fromJson(response.asJsonObject());
  }

  /// Generates chat completions.
  ///
  /// Requires [modelName] to be set.
  Future<SfapChatGenerationsResponseBody> fetchGeneratedChat(
      SfapChatGenerationsRequestBody requestBody) async {
    _requireModelName();
    final request = _buildRequest(
      '/models/$modelName/chat-generations',
      requestBody.toJson(),
    );
    final response = await _restClient.sendAsync(request);
    if (!response.isSuccess) throw SfapApiException.fromResponse(response);
    return SfapChatGenerationsResponseBody.fromJson(response.asJsonObject());
  }

  /// Generates text from a prompt.
  ///
  /// Requires [modelName] to be set.
  Future<SfapGenerationsResponseBody> fetchGeneratedText(String prompt) async {
    _requireModelName();
    final request = _buildRequest(
      '/models/$modelName/generations',
      {'prompt': prompt},
    );
    final response = await _restClient.sendAsync(request);
    if (!response.isSuccess) throw SfapApiException.fromResponse(response);
    return SfapGenerationsResponseBody.fromJson(response.asJsonObject());
  }

  /// Submits feedback for a generation.
  Future<SfapFeedbackResponseBody> submitFeedback(
      SfapFeedbackRequestBody requestBody) async {
    final request = _buildRequest('/feedback', requestBody.toJson());
    final response = await _restClient.sendAsync(request);
    if (!response.isSuccess) throw SfapApiException.fromResponse(response);
    return SfapFeedbackResponseBody.fromJson(response.asJsonObject());
  }

  RestRequest _buildRequest(String pathSuffix, Map<String, dynamic> body) {
    return RestRequest(
      method: RestMethod.post,
      path: 'https://$apiHostName$_basePath$pathSuffix',
      requestBody: body,
      additionalHttpHeaders: generateHeaders(),
    );
  }

  void _requireModelName() {
    if (modelName == null || modelName!.isEmpty) {
      throw StateError('modelName is required for this SFAP API call');
    }
  }
}
