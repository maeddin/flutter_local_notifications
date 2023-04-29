import 'default_style_information.dart';

/// Used to pass the content for an Android notification displayed using the
/// big icon style.
///
/// When used, the bitmap given to [AndroidNotificationDetails.largeIcon] will
/// be shown extra large.
class BigIconStyleInformation extends DefaultStyleInformation {
  /// Constructs an instance of [BigIconStyleInformation].
  const BigIconStyleInformation({
    bool htmlFormatContent = false,
    bool htmlFormatTitle = false,
  }) : super(htmlFormatContent, htmlFormatTitle);
}
