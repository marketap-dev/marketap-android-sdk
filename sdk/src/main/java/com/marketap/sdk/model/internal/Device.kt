package com.marketap.sdk.model.internal


internal data class Device(
    val gaid: String? = null,      // ADID (Android 전용)
    val appSetId: String? = null,  // 앱 설정 ID (Android 전용)
    val appLocalId: String? = null,// 로컬 ID (Android 전용)
    val os: String? = null,                // OS 이름 + 버전 (예: "iOS 17.1", "Android 13", "Windows 11")
    val osVersion: String? = null, // OS 버전만 따로 (예: "17.1", "13.0")
    val libraryVersion: String? = null, // 라이브러리 버전 (예: "1.0.0")
    val model: String? = null,     // 기기 모델명 (예: "iPhone 14 Pro", "Galaxy S23 Ultra")
    val manufacturer: String? = null, // 제조사 (예: "Apple", "Samsung") [iOS에서는 지원 X]
    val brand: String? = null,     // 브랜드 (예: "Pixel", "Samsung", "OnePlus") [Android 전용]
    val token: String? = null,     // 푸시 토큰

    val appVersion: String? = null,       // 앱 버전 (네이티브 앱만)
    val appBuildNumber: String? = null,   // 앱 빌드 번호 (네이티브 앱만)

    val browserName: String? = null,      // 브라우저 이름 (예: "Chrome", "Safari") [Web 전용]
    val browserVersion: String? = null,   // 브라우저 버전 (예: "120.0.0.1") [Web 전용]
    val userAgent: String? = null,        // 사용자 에이전트 정보 [Web 전용]

    val timezone: String? = null,         // 타임존 (예: "Asia/Seoul")
    val locale: String? = null,           // 언어 및 지역 (예: "ko-KR", "en-US")

    val screen: Screen? = null,   // 화면 정보 (nullable 처리)

    val cpuArch: String? = null,      // CPU 아키텍처 (예: "arm64", "x86_64") [Android/iOS 전용]
    val memoryTotal: Int? = null,     // 총 램 용량 (MB 단위) [Android/iOS 지원]
    val storageTotal: Int? = null,    // 총 저장 공간 (GB 단위) [Android/iOS 지원]
    val batteryLevel: Int? = null,    // 배터리 잔량 (%) [Android/iOS 지원]
    val isCharging: Boolean? = null,  // 충전 중 여부 [Android/iOS 지원]

    val networkType: String? = null,  // 네트워크 유형 (예: "wifi", "4G", "5G", "ethernet")
    val carrier: String? = null,      // 이동통신사 (예: "SKT", "Verizon") [Android/iOS 지원]
    val hasSim: Boolean? = null,      // SIM 카드 존재 여부 [Android/iOS 지원]
    val maxTouchPoints: Int? = null,  // 최대 터치 지원 포인트 수 [Web 전용]

    val camera: Boolean? = null,       // 카메라 권한 여부
    val microphone: Boolean? = null,   // 마이크 권한 여부
    val location: Boolean? = null,     // 위치 권한 여부
    val notifications: Boolean? = null, // 알림 권한 여부

    val sessionId: String? = null      // 세션 식별자 (UUID)
)