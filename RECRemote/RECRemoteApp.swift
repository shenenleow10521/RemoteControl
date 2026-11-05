import SwiftUI

@main
struct RECRemoteApp: App {
    @StateObject private var robot = RobotBluetooth()
    var body: some Scene {
        WindowGroup {
            RemoteView().environmentObject(robot).preferredColorScheme(.dark)
        }
    }
}
