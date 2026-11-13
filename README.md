<a href='https://play.google.com/store/apps/details?id=ca.cgagnier.illumidel&utm_source=github'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height='80'/></a>

# Illumidel

Native application for Android and iOS to discover, manage, and control your [Illumidel](https://illumidel.com/) lighting controllers.

### Features
- Automatic device detection (mDNS / Bonjour)
- Real-time device status and controls from one list
- Custom device names and hide or delete options
- Opens control UI immediately when connected to a controller in Access Point mode
- Controller firmware updates and French / English controller UI language switching
- Phone, tablet, and iPad support

## Project Structure
- **`app/`**: Native Android application built with Jetpack Compose and Material 3.
- **`iosApp/`**: Native iOS application built with SwiftUI (see [iosApp/README.md](iosApp/README.md)).
- **`shared/`**: Kotlin Multiplatform (KMP) shared business logic, database, models, and network layer.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) for branch strategy, coding guidelines, and pull request workflows.

## License
This project is licensed under the [GNU General Public License v3.0](LICENSE).

## Disclaimer

This project is not an official Google project. It is not supported by
Google and Google specifically disclaims all warranties as to its quality,
merchantability, or fitness for a particular purpose.

Google Play and the Google Play logo are trademarks of Google LLC.
Apple, the Apple logo, iPhone, and iPad are trademarks of Apple Inc., registered in the U.S. and other countries. App Store is a service mark of Apple Inc.
