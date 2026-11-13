import Foundation
import Shared
import Testing
@testable import Illumidel

struct WLEDTests {

    struct DeepLinkCase: Sendable, CustomTestStringConvertible {
        let urlString: String
        let expectedMac: String?

        var testDescription: String { urlString }
    }

    @Test(arguments: [
        DeepLinkCase(urlString: "illumidel://device/AA:BB:CC:DD:EE:01", expectedMac: "AA:BB:CC:DD:EE:01"),
        DeepLinkCase(urlString: "illumidel://aabbccddeeff", expectedMac: "aabbccddeeff"),
        DeepLinkCase(urlString: "wled://device/aabbccddeeff", expectedMac: nil),
        DeepLinkCase(urlString: "illumidel://device", expectedMac: nil),
        DeepLinkCase(urlString: "illumidel://device/aabbccddeeff/extra", expectedMac: nil)
    ])
    func deepLinkParserParsesMacAddress(_ testCase: DeepLinkCase) throws {
        let url = try #require(URL(string: testCase.urlString))
        #expect(DeepLinkParser.parseMacAddress(url) == testCase.expectedMac)
    }

    @Test func matchesMacAddressIgnoresCaseAndSeparators() {
        #expect(DeepLinkParser.matchesMacAddress("aabbccddee01", "AA:BB:CC:DD:EE:01"))
        #expect(DeepLinkParser.matchesMacAddress("aabbccddee01", "aa-bb-cc-dd-ee-01"))
        #expect(DeepLinkParser.matchesMacAddress("aabbccddee01", "AABBCCDDEE01"))
        #expect(!DeepLinkParser.matchesMacAddress("", ""))
        #expect(!DeepLinkParser.matchesMacAddress("aabbccddee01", "aabbccddee02"))
    }

    @Test func branchNameKeyMapsToIllumidelLanguageLabels() {
        #expect(Branch.stable.nameKey == "Français")
        #expect(Branch.beta.nameKey == "English")
    }
}
