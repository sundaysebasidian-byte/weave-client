// Exercise the production guard against real processes and a captured zero-thread snapshot shape.
#define wmain WeaveSetupMain
#include "../setup/Weave.Setup.cpp"
#undef wmain

namespace {
struct Child {
    PROCESS_INFORMATION info{};
    explicit Child(const fs::path& executable) {
        STARTUPINFOW startup{}; startup.cb = sizeof startup;
        auto command = L"\"" + executable.wstring() + L"\"";
        Require(CreateProcessW(executable.c_str(), command.data(), nullptr, nullptr, FALSE,
            CREATE_SUSPENDED | CREATE_NO_WINDOW, nullptr, executable.parent_path().c_str(), &startup, &info), "Create suspended GUI fixture");
    }
    void Exit() {
        Require(TerminateProcess(info.hProcess, 0), "Exit owned GUI fixture");
        Require(WaitForSingleObject(info.hProcess, 10000) == WAIT_OBJECT_0, "Wait for owned GUI fixture exit");
    }
    ~Child() {
        if (info.hProcess) {
            if (WaitForSingleObject(info.hProcess, 0) == WAIT_TIMEOUT) {
                TerminateProcess(info.hProcess, 1);
                WaitForSingleObject(info.hProcess, 10000);
            }
            CloseHandle(info.hProcess);
        }
        if (info.hThread) CloseHandle(info.hThread);
    }
    Child(const Child&) = delete;
};

DWORD ThreadCount(DWORD pid) {
    Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0));
    Require(snapshot.value != INVALID_HANDLE_VALUE, "Snapshot GUI fixture");
    PROCESSENTRY32W entry{}; entry.dwSize = sizeof entry;
    Require(Process32FirstW(snapshot.value, &entry), "Read GUI fixture snapshot");
    do {
        if (entry.th32ProcessID == pid) return entry.cntThreads;
    } while (Process32NextW(snapshot.value, &entry));
    Require(GetLastError() == ERROR_NO_MORE_FILES, "Complete GUI fixture snapshot");
    return MAXDWORD;
}

void DenyQuery(HANDLE process) {
    PSECURITY_DESCRIPTOR descriptor = nullptr;
    Require(ConvertStringSecurityDescriptorToSecurityDescriptorW(
        L"D:P(D;;0x1000;;;WD)(A;;GA;;;SY)(A;;GA;;;BA)", SDDL_REVISION_1, &descriptor, nullptr), "Build fixture process DACL");
    LocalMemory memory; memory.value = descriptor;
    Require(SetKernelObjectSecurity(process, DACL_SECURITY_INFORMATION, descriptor), "Restrict fixture process query");
}

void DisableDebugPrivilegeForTests() {
    // Keep query-denial fixtures deterministic even if the CI launcher enabled SeDebugPrivilege.
    // This only reduces privileges in this short-lived test process.
    Handle token(nullptr);
    Require(OpenProcessToken(GetCurrentProcess(), TOKEN_ADJUST_PRIVILEGES | TOKEN_QUERY, &token.value), "Open test privilege token");
    TOKEN_PRIVILEGES privilege{}; privilege.PrivilegeCount = 1;
    Require(LookupPrivilegeValueW(nullptr, SE_DEBUG_NAME, &privilege.Privileges[0].Luid), "Resolve test debug privilege");
    Require(AdjustTokenPrivileges(token.value, FALSE, &privilege, 0, nullptr, nullptr), "Disable test debug privilege");
    const DWORD error = GetLastError();
    if (error != ERROR_SUCCESS && error != ERROR_NOT_ALL_ASSIGNED) Fail("Disable test debug privilege", error);
}

void ExpectBlocked(const fs::path& root, DWORD code) {
    try { RequireClosedGui(root); }
    catch (const std::system_error& error) {
        if (static_cast<DWORD>(error.code().value()) != code) throw;
        return;
    }
    Fail("Running GUI was not blocked", ERROR_INVALID_DATA);
}
}

int wmain(int argc, wchar_t** argv) {
    try {
        // Read-only entry point for verifying the actual reporting machine's process list.
        if (argc == 3 && std::wstring(argv[1]) == L"--check") {
            RequireClosedGui(fs::absolute(argv[2]));
            std::cout << "Production GUI process guard passed.\n";
            return 0;
        }
        Require(argc == 2, "Expected a task-owned fixture directory");
        DisableDebugPrivilegeForTests();
        const auto directory = fs::absolute(argv[1]) / (L"gui-fixture-" + std::to_wstring(GetCurrentProcessId()) + L"-" + std::to_wstring(GetTickCount64()));
        const auto root = directory / L"installation";
        const auto installed = root / L"versions" / L"test" / L"Weave.Windows.exe";
        const auto foreign = directory / L"foreign" / L"Weave.Windows.exe";
        fs::create_directories(installed.parent_path());
        fs::create_directories(foreign.parent_path());
        wchar_t self[32768];
        const DWORD size = GetModuleFileNameW(nullptr, self, 32768);
        Require(size > 0 && size < 32768, "Locate native test executable");
        fs::copy_file(self, installed);
        fs::copy_file(self, foreign);
        {
            Child live(installed);
            Require(ThreadCount(live.info.dwProcessId) > 0, "Suspended GUI must retain an execution thread");
            ExpectBlocked(root, ERROR_INSTALL_ALREADY_RUNNING);
            std::cout << "PASS: live installed GUI still blocks maintenance.\n";
        }
        {
            Child live(foreign);
            RequireClosedGui(root);
            std::cout << "PASS: readable foreign GUI does not block this installation.\n";
            DenyQuery(live.info.hProcess);
            Handle denied(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, live.info.dwProcessId));
            const DWORD error = GetLastError();
            Require(!denied.value && error == ERROR_ACCESS_DENIED, "Fixture must deny process query");
            ExpectBlocked(root, ERROR_ACCESS_DENIED);
            std::cout << "PASS: inaccessible live GUI remains fail-closed.\n";
            // Normal exits disappear from Toolhelp immediately on many Windows builds.
            // Replay the reporting machine's delayed-teardown record against a real
            // query-denied process, so this regression cannot silently pass without
            // exercising the exact zero-thread/access-denied combination.
            PROCESSENTRY32W exited{}; exited.dwSize = sizeof exited;
            exited.th32ProcessID = live.info.dwProcessId;
            wcscpy_s(exited.szExeFile, L"Weave.Windows.exe");
            RequireClosedGuiProcess(root, exited);
            std::cout << "PASS: captured zero-thread query-denied record is ignored.\n";
            live.Exit();
            const auto threads = ThreadCount(live.info.dwProcessId);
            Require(threads == 0 || threads == MAXDWORD, "Exited GUI must be absent or have zero threads");
            RequireClosedGui(root);
            std::cout << "PASS: retained exited GUI does not block maintenance.\n";
        }
        return 0;
    }
    catch (const std::exception& error) { std::cerr << error.what() << '\n'; return 1; }
}
