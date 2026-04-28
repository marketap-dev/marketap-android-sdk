#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
cd "$REPO_ROOT"

OUTPUT_PATH="$REPO_ROOT/llms.txt"
PRINT_STDOUT="false"

usage() {
  cat <<'EOF'
Usage:
  ./generate_llms.sh
  ./generate_llms.sh --stdout
  ./generate_llms.sh --output path/to/llms.txt
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stdout)
      PRINT_STDOUT="true"
      shift
      ;;
    --output|-o)
      OUTPUT_PATH="${2:-}"
      if [[ -z "$OUTPUT_PATH" ]]; then
        echo "Missing value for $1" >&2
        exit 1
      fi
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

export LLMS_OUTPUT_PATH="$OUTPUT_PATH"
export LLMS_PRINT_STDOUT="$PRINT_STDOUT"
export LLMS_REPO_ROOT="$REPO_ROOT"

node <<'NODE'
const fs = require("fs");
const path = require("path");
const cp = require("child_process");

const repoRoot = process.env.LLMS_REPO_ROOT;
const outputPath = process.env.LLMS_OUTPUT_PATH;
const printStdout = process.env.LLMS_PRINT_STDOUT === "true";
const repoName = path.posix.basename(repoRoot);

function sh(command) {
  try {
    return cp.execSync(command, {
      cwd: repoRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
  } catch (_) {
    return "";
  }
}

function parseRepoSlug(remote) {
  const sshMatch = remote.match(/^git@github\.com:(.+?)(?:\.git)?$/);
  if (sshMatch) return sshMatch[1];

  const httpsMatch = remote.match(/^https:\/\/github\.com\/(.+?)(?:\.git)?$/);
  if (httpsMatch) return httpsMatch[1];

  return "";
}

function encodeGitHubPath(filePath) {
  return filePath
    .split("/")
    .map(segment => encodeURIComponent(segment))
    .join("/");
}

const fallbackRepoSlug = {
  "marketap-web-sdk": "marketap-dev/marketap-web-sdk",
  "marketap-android-sdk": "marketap-dev/marketap-android-sdk",
  "marketap-ios-sdk": "marketap-dev/marketap-ios-sdk",
  "marketap-flutter-sdk": "marketap-dev/marketap-flutter-sdk",
  "react-native-sdk": "marketap-dev/marketap-react-native-sdk",
};

const repoConfigs = {
  "marketap-web-sdk": {
    title: "Marketap Web SDK",
    summary:
      "Browser SDK for Marketap event tracking, identity management, page-view enrichment, web push, and in-app campaign delivery.",
    notes:
      "This file intentionally links only the main integration docs, public entrypoints, and runtime files. Example pages, generated assets, and repository-wide file indexes are omitted.",
    sections: [
      {
        title: "Documentation",
        items: [
          {
            title: "README",
            path: "README.md",
            description: "Repository overview and the starting point for web integration.",
          },
          {
            title: "Package Manifest",
            path: "package.json",
            description: "Published package metadata, scripts, and dependency surface.",
          },
        ],
      },
      {
        title: "API",
        items: [
          {
            title: "Public SDK Interface",
            path: "src/marketapSdk.ts",
            description: "TypeScript interface for initialization, identity, event tracking, revenue, and device opt-in APIs.",
          },
          {
            title: "SDK Implementation",
            path: "src/client.ts",
            description: "Main browser implementation that initializes the SDK, manages identity, and dispatches tracking calls.",
          },
          {
            title: "Package Entrypoint",
            path: "src/index.ts",
            description: "Default export that exposes the SDK and binds it to `window.mtap` in browser environments.",
          },
        ],
      },
      {
        title: "Runtime",
        items: [
          {
            title: "Dependency Wiring",
            path: "src/presentation/dependency.ts",
            description: "Service graph assembly for API clients, browser integrations, push, and in-app orchestration.",
          },
          {
            title: "Service Worker Source",
            path: "src/worker/serviceWorker.ts",
            description: "Source for the web push service worker runtime, including notification display and click handling.",
          },
          {
            title: "Distributed Service Worker",
            path: "service-worker.js",
            description: "Root service worker artifact used when integrating web push into host sites.",
          },
        ],
      },
    ],
  },
  "marketap-android-sdk": {
    title: "Marketap Android SDK",
    summary:
      "Native Android SDK for Marketap analytics, identity, push notifications, in-app campaigns, and web-to-native bridge flows.",
    notes:
      "This file intentionally excludes local build files, sample app credentials, and generated directories. It focuses on the docs and public Android integration surface.",
    sections: [
      {
        title: "Documentation",
        items: [
          {
            title: "README",
            path: "README.md",
            description: "Repository overview, installation snippet, and Android-specific integration notes.",
          },
          {
            title: "Official Android Guide",
            url: "https://docs.marketap.io/t3ZS4WXNMj0HK27EtIMV/developer/push-notification/integration/android",
            description: "Hosted Marketap documentation for Android push and SDK integration.",
          },
        ],
      },
      {
        title: "Installation",
        items: [
          {
            title: "SDK Gradle Module",
            path: "sdk/build.gradle.kts",
            description: "Android library module definition, dependency graph, and publication-facing build settings.",
          },
          {
            title: "JitPack Configuration",
            path: "jitpack.yml",
            description: "Build configuration used for JitPack-based artifact verification and publication.",
          },
        ],
      },
      {
        title: "API",
        items: [
          {
            title: "Marketap API",
            path: "sdk/src/main/kotlin/com/marketap/sdk/Marketap.kt",
            description: "Primary Android entrypoint for initialization, identity, event tracking, revenue, and push opt-in updates.",
          },
          {
            title: "Marketap Web Bridge",
            path: "sdk/src/main/kotlin/com/marketap/sdk/MarketapWebBridge.kt",
            description: "WebView bridge that forwards web events and in-app interactions into the native SDK.",
          },
          {
            title: "Marketap Plugin API",
            path: "sdk/src/main/kotlin/com/marketap/sdk/MarketapPlugin.kt",
            description: "Plugin-oriented API used by wrapper SDKs for in-app events, web bridge coordination, and integration metadata.",
          },
        ],
      },
      {
        title: "Push And In-App",
        items: [
          {
            title: "Firebase Messaging Service",
            path: "sdk/src/main/kotlin/com/marketap/sdk/client/push/MarketapFirebaseMessagingService.kt",
            description: "Firebase messaging integration for receiving and opening Marketap push notifications.",
          },
          {
            title: "In-App Message Activity",
            path: "sdk/src/main/kotlin/com/marketap/sdk/client/inapp/InAppMessageActivity.kt",
            description: "Android activity responsible for presenting in-app message content.",
          },
        ],
      },
    ],
  },
  "marketap-ios-sdk": {
    title: "Marketap iOS SDK",
    summary:
      "Native iOS SDK for Marketap analytics, identity, push notification handling, in-app campaigns, and web bridge integrations.",
    notes:
      "This file intentionally omits Xcode user state, example applications, and generated workspace metadata. It focuses on the install targets and public iOS entrypoints.",
    sections: [
      {
        title: "Documentation",
        items: [
          {
            title: "README",
            path: "README.md",
            description: "Repository overview and the starting point for iOS integration.",
          },
          {
            title: "Official iOS Guide",
            url: "https://docs.marketap.io/t3ZS4WXNMj0HK27EtIMV/developer/push-notification/integration/ios",
            description: "Hosted Marketap documentation for iOS push and SDK integration.",
          },
        ],
      },
      {
        title: "Installation",
        items: [
          {
            title: "Swift Package",
            path: "Package.swift",
            description: "Swift Package Manager manifest for the main SDK and notification service extension targets.",
          },
          {
            title: "CocoaPods SDK Spec",
            path: "MarketapSDK.podspec",
            description: "CocoaPods package definition for the main iOS SDK.",
          },
          {
            title: "Notification Extension Podspec",
            path: "MarketapSDKNotificationServiceExtension.podspec",
            description: "CocoaPods package definition for the notification service extension target.",
          },
        ],
      },
      {
        title: "API",
        items: [
          {
            title: "Marketap Entrypoint",
            path: "Sources/MarketapSDK/Marketap.swift",
            description: "Primary iOS entrypoint for initialization and access to the shared SDK client.",
          },
          {
            title: "Tracking API",
            path: "Sources/MarketapSDK/Marketap+Track.swift",
            description: "Identity, event tracking, page-view, purchase, and revenue APIs exposed to host apps.",
          },
          {
            title: "Notification API",
            path: "Sources/MarketapSDK/Marketap+Notification.swift",
            description: "Push token, notification delegate, and authorization APIs exposed to host apps.",
          },
        ],
      },
      {
        title: "Bridge And Extensions",
        items: [
          {
            title: "Marketap Web Bridge",
            path: "Sources/MarketapSDK/Bridge/MarketapWebBridge.swift",
            description: "WKWebView bridge for forwarding web events and routing in-app campaigns to web content.",
          },
          {
            title: "Marketap Plugin API",
            path: "Sources/MarketapSDK/MarketapPlugin.swift",
            description: "Plugin-facing APIs used by wrapper SDKs for in-app tracking and bridge coordination.",
          },
          {
            title: "Notification Service",
            path: "Sources/MarketapSDKNotificationServiceExtension/MarketapNotificationService.swift",
            description: "Notification service extension implementation for handling Marketap push payloads on iOS.",
          },
        ],
      },
    ],
  },
  "marketap-flutter-sdk": {
    title: "Marketap Flutter SDK",
    summary:
      "Flutter plugin that wraps the Marketap native SDKs and exposes analytics, identity, push, in-app, and web bridge APIs to Dart.",
    notes:
      "This file intentionally excludes sample app state, CocoaPods vendor directories, and generated platform artifacts. It points only to the main Flutter integration surface.",
    sections: [
      {
        title: "Documentation",
        items: [
          {
            title: "README",
            path: "README.md",
            description: "Repository overview and the first place to refine Flutter integration guidance.",
          },
          {
            title: "Pubspec",
            path: "pubspec.yaml",
            description: "Flutter plugin manifest, supported platforms, and published package metadata.",
          },
        ],
      },
      {
        title: "API",
        items: [
          {
            title: "Dart Entrypoint",
            path: "lib/marketap_sdk.dart",
            description: "Public Flutter API for initialization, identity, event tracking, revenue, push, and click handling.",
          },
          {
            title: "Plugin Helpers",
            path: "lib/src/marketap_plugin.dart",
            description: "Dart-side plugin helpers for web bridge events and in-app interaction reporting.",
          },
          {
            title: "Flutter Web Bridge",
            path: "lib/src/marketap_web_bridge.dart",
            description: "Bridge between Flutter WebViews and the native Marketap SDKs for in-app and event flows.",
          },
        ],
      },
      {
        title: "Native Integration",
        items: [
          {
            title: "iOS Podspec",
            path: "ios/marketap_sdk.podspec",
            description: "iOS package definition used when the Flutter plugin integrates with CocoaPods.",
          },
          {
            title: "Android Plugin",
            path: "android/src/main/kotlin/com/marketap/flutter/sdk/MarketapSdkPlugin.kt",
            description: "Flutter Android plugin entrypoint that binds Dart method channels to the native Marketap SDK.",
          },
          {
            title: "Flutter Bridge Registry",
            path: "android/src/main/kotlin/com/marketap/flutter/sdk/FlutterBridgeRegistry.kt",
            description: "Android-side bridge registry used to connect Flutter web bridge flows to the native SDK runtime.",
          },
        ],
      },
    ],
  },
  "react-native-sdk": {
    title: "Marketap React Native SDK",
    summary:
      "React Native wrapper around the Marketap native SDKs, with TypeScript APIs for analytics, identity, push notifications, in-app messages, and web bridge coordination.",
    notes:
      "This file intentionally excludes example apps, Pods, local Xcode state, and generated lockfiles. It keeps only the package surface and native bridge files that matter for integration.",
    sections: [
      {
        title: "Documentation",
        items: [
          {
            title: "README",
            path: "README.md",
            description: "Repository overview, requirements, installation steps, and quick-start usage for React Native apps.",
          },
          {
            title: "Package Manifest",
            path: "package.json",
            description: "Published package metadata, dependency ranges, scripts, and React Native package surface.",
          },
        ],
      },
      {
        title: "Installation",
        items: [
          {
            title: "CocoaPods Podspec",
            path: "react-native-marketap-sdk.podspec",
            description: "iOS package definition for the React Native wrapper and native Marketap dependency.",
          },
        ],
      },
      {
        title: "API",
        items: [
          {
            title: "TypeScript Entrypoint",
            path: "src/index.tsx",
            description: "Public React Native API surface that exports initialization, identity, tracking, push, and click APIs.",
          },
          {
            title: "Core Wrapper",
            path: "src/core/MarketapCore.ts",
            description: "Core TypeScript wrapper around the platform native modules and event emitters.",
          },
          {
            title: "React Native Web Bridge",
            path: "src/MarketapWebBridge.ts",
            description: "Bridge for coordinating Marketap web content and native in-app message delivery in React Native apps.",
          },
        ],
      },
      {
        title: "Native Modules",
        items: [
          {
            title: "Android Module",
            path: "android/src/main/java/com/marketapsdk/MarketapSdkModule.kt",
            description: "Android native module that forwards React Native calls into the Marketap Android SDK.",
          },
          {
            title: "Android Package",
            path: "android/src/main/java/com/marketapsdk/MarketapSdkPackage.kt",
            description: "React Package registration for exposing the Marketap Android module to React Native.",
          },
          {
            title: "iOS Module",
            path: "ios/MarketapSdk.swift",
            description: "iOS native module that forwards React Native calls and events into the Marketap iOS SDK.",
          },
        ],
      },
    ],
  },
};

const config = repoConfigs[repoName];
if (!config) {
  throw new Error(`No llms.txt configuration found for repository: ${repoName}`);
}

const remote = sh("git config --get remote.origin.url");
const repoSlug = parseRepoSlug(remote) || fallbackRepoSlug[repoName] || "";
const ref = sh("git symbolic-ref --short -q HEAD") || sh("git rev-parse HEAD") || "main";

function resolveItemUrl(item) {
  if (item.url) return item.url;
  if (!item.path) {
    throw new Error(`Item '${item.title}' is missing both 'url' and 'path'.`);
  }

  const absolutePath = path.join(repoRoot, item.path);
  if (!fs.existsSync(absolutePath)) {
    throw new Error(`Configured path does not exist: ${item.path}`);
  }

  return `https://raw.githubusercontent.com/${repoSlug}/${ref}/${encodeGitHubPath(item.path)}`;
}

const lines = [];
lines.push(`# ${config.title}`);
lines.push("");
lines.push(`> ${config.summary}`);
lines.push("");
lines.push(config.notes);

for (const section of config.sections) {
  lines.push("");
  lines.push(`## ${section.title}`);
  for (const item of section.items) {
    lines.push(`- [${item.title}](${resolveItemUrl(item)}): ${item.description}`);
  }
}

const finalText = `${lines.join("\n")}\n`;

if (printStdout) {
  process.stdout.write(finalText);
} else {
  fs.writeFileSync(outputPath, finalText, "utf8");
  process.stdout.write(`Wrote ${outputPath}\n`);
}
NODE
