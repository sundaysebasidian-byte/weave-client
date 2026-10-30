#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <aclapi.h>
#include <sddl.h>
#include <shlobj.h>
#include <winsvc.h>
#include <tlhelp32.h>
#include <wrl/client.h>
#include <array>
#include <filesystem>
#include <iostream>
#include <string>
#include <vector>
#include <system_error>

namespace fs = std::filesystem;
namespace {
constexpr wchar_t ServiceName[] = L"WeaveCore";
constexpr DWORD Mutation = FILE_WRITE_DATA | FILE_APPEND_DATA | FILE_WRITE_EA | FILE_WRITE_ATTRIBUTES | DELETE | FILE_DELETE_CHILD | WRITE_DAC | WRITE_OWNER | GENERIC_WRITE | GENERIC_ALL;
constexpr DWORD AncestorMutation = DELETE | FILE_DELETE_CHILD | WRITE_DAC | WRITE_OWNER | GENERIC_ALL;
struct Handle {
    HANDLE value = INVALID_HANDLE_VALUE;
    explicit Handle(HANDLE v) : value(v) {}
    ~Handle() { if (value != INVALID_HANDLE_VALUE && value) CloseHandle(value); }
    Handle(const Handle&) = delete;
};
struct ServiceHandle {
    SC_HANDLE value = nullptr;
    explicit ServiceHandle(SC_HANDLE v) : value(v) {}
    ~ServiceHandle() { if (value) CloseServiceHandle(value); }
    ServiceHandle(const ServiceHandle&) = delete;
};
struct LocalMemory {
    HLOCAL value = nullptr;
    ~LocalMemory() { if (value) LocalFree(value); }
};
[[noreturn]] void Fail(const char* message, DWORD code = GetLastError()) {
    throw std::system_error(static_cast<int>(code ? code : ERROR_INVALID_DATA), std::system_category(), message);
}
void Require(bool value, const char* message) { if (!value) Fail(message); }
bool Same(const std::wstring& a, const std::wstring& b) { return _wcsicmp(a.c_str(), b.c_str()) == 0; }
bool ReleaseId(const std::wstring& id) {
    if (id.size() != 57 || id[40] != L'-') return false;
    for (size_t i = 0; i < id.size(); ++i)
        if (i != 40 && !((id[i] >= L'0' && id[i] <= L'9') || (id[i] >= L'a' && id[i] <= L'f'))) return false;
    return true;
}
bool Trusted(PSID sid) {
    if (IsWellKnownSid(sid, WinLocalSystemSid) || IsWellKnownSid(sid, WinBuiltinAdministratorsSid)) return true;
    static const auto installer = [] {
        std::vector<BYTE> sid(SECURITY_MAX_SID_SIZE); DWORD size = static_cast<DWORD>(sid.size());
        wchar_t domain[256]; DWORD domainSize = 256; SID_NAME_USE use;
        Require(LookupAccountNameW(nullptr, L"NT SERVICE\\TrustedInstaller", sid.data(), &size, domain, &domainSize, &use), "Resolve TrustedInstaller");
        sid.resize(size); return sid;
    }();
    return EqualSid(sid, const_cast<BYTE*>(installer.data())) != FALSE;
}
void CheckEntry(const fs::path& path, bool ancestor = false) {
    const DWORD attributes = GetFileAttributesW(path.c_str());
    Require(attributes != INVALID_FILE_ATTRIBUTES && !(attributes & FILE_ATTRIBUTE_REPARSE_POINT), "Unsafe or missing installation path");
    PSID owner = nullptr; PACL acl = nullptr; PSECURITY_DESCRIPTOR descriptor = nullptr;
    const DWORD result = GetNamedSecurityInfoW(path.c_str(), SE_FILE_OBJECT, OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION,
        &owner, nullptr, &acl, nullptr, &descriptor);
    LocalMemory memory; memory.value = descriptor;
    if (result != ERROR_SUCCESS) Fail("Read installation security", result);
    if (!owner || !Trusted(owner) || !acl) Fail("Unsafe installation owner or DACL", ERROR_ACCESS_DENIED);
    for (DWORD i = 0; i < acl->AceCount; ++i) {
        void* entry = nullptr;
        Require(GetAce(acl, i, &entry), "Read installation ACE");
        const auto header = static_cast<ACE_HEADER*>(entry);
        if (header->AceFlags & INHERIT_ONLY_ACE) continue;
        if (header->AceType == ACCESS_DENIED_ACE_TYPE) continue;
        if (header->AceType != ACCESS_ALLOWED_ACE_TYPE) Fail("Unsupported installation ACL entry", ERROR_ACCESS_DENIED);
        const auto allow = static_cast<ACCESS_ALLOWED_ACE*>(entry);
        if (!Trusted(&allow->SidStart) && (allow->Mask & (ancestor ? AncestorMutation : Mutation)))
            Fail("An untrusted identity can change installation files", ERROR_ACCESS_DENIED);
    }
}
void CheckTree(const fs::path& path) {
    CheckEntry(path);
    for (const auto& entry : fs::recursive_directory_iterator(path)) {
        // Reject a junction before the iterator can descend into it.
        CheckEntry(entry.path());
    }
}
void CheckAncestors(fs::path path) {
    while (path.has_parent_path() && path.parent_path() != path) {
        path = path.parent_path(); CheckEntry(path, true);
    }
}
void CreateProtected(const fs::path& path) {
    if (fs::exists(path)) { CheckEntry(path); return; }
    PSECURITY_DESCRIPTOR descriptor = nullptr;
    Require(ConvertStringSecurityDescriptorToSecurityDescriptorW(
        L"O:BAG:BAD:P(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)(A;OICI;GRGX;;;BU)", SDDL_REVISION_1, &descriptor, nullptr), "Create installation ACL");
    LocalMemory memory; memory.value = descriptor;
    SECURITY_ATTRIBUTES attributes{sizeof attributes, descriptor, FALSE};
    Require(CreateDirectoryW(path.c_str(), &attributes), "Create protected installation directory");
    CheckEntry(path);
}
fs::path KnownFolder(REFKNOWNFOLDERID id) {
    PWSTR value = nullptr;
    const HRESULT result = SHGetKnownFolderPath(id, 0, nullptr, &value);
    if (FAILED(result)) Fail("Resolve installation folder", static_cast<DWORD>(result));
    const fs::path path(value); CoTaskMemFree(value); return path;
}
fs::path MachineRoot(const wchar_t* supplied) {
    const fs::path root = KnownFolder(FOLDERID_ProgramFilesX64) / L"Weave";
    if (!Same(root.wstring(), supplied)) Fail("Only the fixed machine installation directory is supported", ERROR_ACCESS_DENIED);
    CheckAncestors(root);
    if (fs::exists(root)) CheckTree(root);
    return root;
}
fs::path Image(const fs::path& root, const std::wstring& id) { return root / L"versions" / id / L"service" / L"Weave.Core.Service.exe"; }
std::wstring Quoted(const fs::path& path) { return L"\"" + path.wstring() + L"\""; }
std::wstring ImageId(const fs::path& root, const std::wstring& image) {
    const std::wstring prefix = L"\"" + (root / L"versions").wstring() + L"\\";
    const std::wstring suffix = L"\\service\\Weave.Core.Service.exe\"";
    if (image.size() != prefix.size() + 57 + suffix.size() || !Same(image.substr(0, prefix.size()), prefix) ||
        !Same(image.substr(image.size() - suffix.size()), suffix)) Fail("The existing service belongs to another installation", ERROR_ACCESS_DENIED);
    const auto id = image.substr(prefix.size(), 57);
    if (!ReleaseId(id)) Fail("Invalid installed release identifier", ERROR_INVALID_DATA);
    return id;
}
struct State { bool exists = false; std::wstring id; DWORD start = SERVICE_AUTO_START; bool running = false; };
fs::path Gui(const fs::path& root, const std::wstring& id) { return root / L"versions" / id / L"Weave.Windows.exe"; }
std::array<fs::path, 2> Shortcuts() {
    return {KnownFolder(FOLDERID_CommonPrograms) / L"Weave" / L"Weave.lnk", KnownFolder(FOLDERID_PublicDesktop) / L"Weave.lnk"};
}
bool ShortcutExists(const fs::path& path) {
    if (GetFileAttributesW(path.c_str()) != INVALID_FILE_ATTRIBUTES) { CheckEntry(path); return true; }
    const DWORD error = GetLastError();
    if (error == ERROR_FILE_NOT_FOUND || error == ERROR_PATH_NOT_FOUND) return false;
    Fail("Inspect shortcut path", error);
}
std::vector<BYTE> ReadSmallFile(const fs::path& path) {
    CheckEntry(path);
    Handle file(CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr));
    Require(file.value != INVALID_HANDLE_VALUE, "Open shortcut recovery input");
    LARGE_INTEGER size{};
    Require(GetFileSizeEx(file.value, &size) && size.QuadPart > 0 && size.QuadPart <= 65536, "Invalid shortcut recovery input size");
    std::vector<BYTE> bytes(static_cast<size_t>(size.QuadPart)); DWORD read = 0;
    Require(ReadFile(file.value, bytes.data(), static_cast<DWORD>(bytes.size()), &read, nullptr) && read == bytes.size(), "Read shortcut recovery input");
    return bytes;
}
void WriteNewFile(const fs::path& path, const std::vector<BYTE>& bytes) {
    Handle file(CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr));
    Require(file.value != INVALID_HANDLE_VALUE, "Create shortcut recovery file");
    DWORD written = 0;
    Require(WriteFile(file.value, bytes.data(), static_cast<DWORD>(bytes.size()), &written, nullptr) && written == bytes.size() &&
        FlushFileBuffers(file.value), "Persist shortcut recovery file");
}
std::wstring ShortcutTarget(const fs::path& path) {
    ReadSmallFile(path);
    Microsoft::WRL::ComPtr<IShellLinkW> link;
    HRESULT result = CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(link.GetAddressOf()));
    if (FAILED(result)) Fail("Open shell link reader", static_cast<DWORD>(result));
    Microsoft::WRL::ComPtr<IPersistFile> file;
    result = link.As(&file);
    if (FAILED(result)) Fail("Open shell link persistence", static_cast<DWORD>(result));
    result = file->Load(path.c_str(), STGM_READ);
    if (FAILED(result)) Fail("Read installed shortcut", static_cast<DWORD>(result));
    wchar_t target[32768]{}; wchar_t arguments[32768]{};
    result = link->GetPath(target, 32768, nullptr, SLGP_RAWPATH);
    if (FAILED(result) || !target[0]) Fail("Invalid installed shortcut target", ERROR_INVALID_DATA);
    result = link->GetArguments(arguments, 32768);
    if (FAILED(result) || arguments[0]) Fail("Unexpected installed shortcut arguments", ERROR_INVALID_DATA);
    return target;
}
void SnapshotShortcuts(const fs::path& root, const std::wstring& next, const State& previous) {
    const auto directory = root / L"setup-shortcuts" / next;
    CreateProtected(directory.parent_path());
    if (fs::exists(directory)) Fail("Shortcut recovery snapshot already exists", ERROR_ALREADY_EXISTS);
    CreateProtected(directory);
    std::vector<BYTE> present;
    const auto paths = Shortcuts();
    for (size_t i = 0; i < paths.size(); ++i) {
        const auto& path = paths[i];
        if (!fs::exists(path.parent_path())) {
            CheckAncestors(path.parent_path()); CreateProtected(path.parent_path());
        }
        CheckAncestors(path);
        const bool exists = ShortcutExists(path);
        if (exists) {
            if (!previous.exists || !Same(ShortcutTarget(path), Gui(root, previous.id).wstring()))
                Fail("An existing shortcut belongs to another installation", ERROR_ACCESS_DENIED);
            WriteNewFile(directory / (std::to_wstring(i) + L".lnk"), ReadSmallFile(path));
        }
        present.push_back(exists ? '1' : '0');
    }
    // Publish the setup journal only after both snapshots and this presence map are durable.
    WriteNewFile(directory / L"present.txt", present);
}
void RestoreShortcuts(const fs::path& root, const std::wstring& next, const State& previous) {
    const auto directory = root / L"setup-shortcuts" / next;
    const auto present = ReadSmallFile(directory / L"present.txt");
    const auto paths = Shortcuts();
    if (present.size() != paths.size()) Fail("Damaged shortcut recovery map", ERROR_INVALID_DATA);
    std::array<std::vector<BYTE>, 2> backups;
    for (size_t i = 0; i < paths.size(); ++i) {
        CheckAncestors(paths[i]);
        const auto backup = directory / (std::to_wstring(i) + L".lnk");
        if ((present[i] != '0' && present[i] != '1') || (present[i] == '1') != fs::exists(backup))
            Fail("Incomplete shortcut recovery snapshot", ERROR_INVALID_DATA);
        if (present[i] == '1') {
            if (!previous.exists || !Same(ShortcutTarget(backup), Gui(root, previous.id).wstring()))
                Fail("Shortcut recovery target changed", ERROR_INVALID_DATA);
            backups[i] = ReadSmallFile(backup);
        }
        if (ShortcutExists(paths[i])) {
            const auto target = ShortcutTarget(paths[i]);
            if (!Same(target, Gui(root, next).wstring()) && (!previous.exists || !Same(target, Gui(root, previous.id).wstring())))
                Fail("A shortcut changed during installation; recovery snapshot retained", ERROR_ACCESS_DENIED);
        }
    }
    for (size_t i = 0; i < paths.size(); ++i) {
        if (present[i] == '1') {
            const fs::path temp = paths[i].wstring() + L".weave-" + next + L".new";
            // Resume a fully written recovery file only when its protected bytes match.
            if (fs::exists(temp)) {
                if (ReadSmallFile(temp) != backups[i]) Fail("Incomplete shortcut recovery write retained", ERROR_INVALID_DATA);
            }
            else WriteNewFile(temp, backups[i]);
            Require(MoveFileExW(temp.c_str(), paths[i].c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH), "Restore previous shortcut");
        }
        else if (ShortcutExists(paths[i])) Require(DeleteFileW(paths[i].c_str()), "Remove incomplete installation shortcut");
    }
}
SERVICE_STATUS_PROCESS Status(SC_HANDLE service) {
    SERVICE_STATUS_PROCESS status{}; DWORD needed = 0;
    Require(QueryServiceStatusEx(service, SC_STATUS_PROCESS_INFO, reinterpret_cast<BYTE*>(&status), sizeof status, &needed), "Query core service status");
    return status;
}
State InspectService(SC_HANDLE manager, const fs::path& root, bool verifyFiles = true) {
    ServiceHandle service(OpenServiceW(manager, ServiceName, SERVICE_QUERY_CONFIG | SERVICE_QUERY_STATUS));
    if (!service.value) { if (GetLastError() == ERROR_SERVICE_DOES_NOT_EXIST) return {}; Fail("Open existing core service"); }
    DWORD needed = 0;
    QueryServiceConfigW(service.value, nullptr, 0, &needed);
    if (GetLastError() != ERROR_INSUFFICIENT_BUFFER || needed > 65536) Fail("Invalid core service configuration");
    std::vector<BYTE> data(needed);
    auto config = reinterpret_cast<QUERY_SERVICE_CONFIGW*>(data.data());
    Require(QueryServiceConfigW(service.value, config, needed, &needed), "Read core service configuration");
    if (config->dwServiceType != SERVICE_WIN32_OWN_PROCESS || !Same(config->lpServiceStartName, L"LocalSystem") ||
        (config->dwStartType != SERVICE_AUTO_START && config->dwStartType != SERVICE_DEMAND_START))
        Fail("The existing core service has unexpected configuration", ERROR_ACCESS_DENIED);
    auto id = ImageId(root, config->lpBinaryPathName);
    if (verifyFiles) CheckTree(root / L"versions" / id);
    return {true, id, config->dwStartType, Status(service.value).dwCurrentState != SERVICE_STOPPED};
}
void Stop(SC_HANDLE manager, const fs::path& root, const std::wstring& id) {
    const auto state = InspectService(manager, root, false);
    if (!state.exists) return;
    if (state.id != id) Fail("Service ownership changed before stop", ERROR_ACCESS_DENIED);
    ServiceHandle service(OpenServiceW(manager, ServiceName, SERVICE_QUERY_STATUS | SERVICE_STOP));
    Require(service.value != nullptr, "Open core service for stop");
    auto status = Status(service.value);
    Handle process(status.dwProcessId ? OpenProcess(SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION, FALSE, status.dwProcessId) : nullptr);
    if (status.dwProcessId) {
        Require(process.value != nullptr, "Retain core service process");
        wchar_t image[32768]; DWORD length = 32768;
        Require(QueryFullProcessImageNameW(process.value, 0, image, &length), "Read core service process image");
        if (!Same(image, Image(root, id).wstring())) Fail("Core service process ownership changed", ERROR_ACCESS_DENIED);
    }
    if (status.dwCurrentState != SERVICE_STOPPED && status.dwCurrentState != SERVICE_STOP_PENDING) {
        SERVICE_STATUS ignored{};
        if (!ControlService(service.value, SERVICE_CONTROL_STOP, &ignored) && GetLastError() != ERROR_SERVICE_NOT_ACTIVE) Fail("Stop core service");
    }
    const auto deadline = GetTickCount64() + 30000;
    while (Status(service.value).dwCurrentState != SERVICE_STOPPED) {
        if (GetTickCount64() >= deadline) Fail("Core service did not stop; installation files retained", ERROR_TIMEOUT);
        Sleep(100);
    }
    if (process.value && WaitForSingleObject(process.value, 5000) != WAIT_OBJECT_0)
        Fail("Core service exit is unconfirmed; installation files retained", ERROR_TIMEOUT);
}
void Start(SC_HANDLE manager, const fs::path& root, const std::wstring& id) {
    const auto state = InspectService(manager, root);
    if (!state.exists || state.id != id) Fail("Service ownership changed before start", ERROR_ACCESS_DENIED);
    ServiceHandle service(OpenServiceW(manager, ServiceName, SERVICE_START | SERVICE_QUERY_STATUS));
    Require(service.value != nullptr, "Open core service for start");
    if (!StartServiceW(service.value, 0, nullptr) && GetLastError() != ERROR_SERVICE_ALREADY_RUNNING) Fail("Start core service");
    const auto deadline = GetTickCount64() + 30000;
    while (true) {
        const auto status = Status(service.value);
        if (status.dwCurrentState == SERVICE_RUNNING) return;
        if (status.dwCurrentState == SERVICE_STOPPED) Fail("Core service rejected the installation", status.dwWin32ExitCode);
        if (GetTickCount64() >= deadline) Fail("Core service startup timed out", ERROR_TIMEOUT);
        Sleep(100);
    }
}
void Configure(SC_HANDLE manager, const fs::path& root, const std::wstring& id, DWORD start, bool create) {
    ServiceHandle service(create ? CreateServiceW(manager, ServiceName, L"Weave Core", SERVICE_ALL_ACCESS,
        SERVICE_WIN32_OWN_PROCESS, start, SERVICE_ERROR_NORMAL, Quoted(Image(root, id)).c_str(), nullptr, nullptr, nullptr, L"LocalSystem", nullptr)
        : OpenServiceW(manager, ServiceName, SERVICE_ALL_ACCESS));
    Require(service.value != nullptr, "Create or open installed core service");
    if (!create) Require(ChangeServiceConfigW(service.value, SERVICE_NO_CHANGE, start, SERVICE_NO_CHANGE,
        Quoted(Image(root, id)).c_str(), nullptr, nullptr, nullptr, nullptr, nullptr, nullptr), "Switch core service version");
    SERVICE_SID_INFO sid{SERVICE_SID_TYPE_UNRESTRICTED};
    Require(ChangeServiceConfig2W(service.value, SERVICE_CONFIG_SERVICE_SID_INFO, &sid), "Set service SID");
    wchar_t privileges[] = L"SeChangeNotifyPrivilege\0SeLoadDriverPrivilege\0";
    SERVICE_REQUIRED_PRIVILEGES_INFOW required{privileges};
    Require(ChangeServiceConfig2W(service.value, SERVICE_CONFIG_REQUIRED_PRIVILEGES_INFO, &required), "Restrict service privileges");
    PSECURITY_DESCRIPTOR descriptor = nullptr;
    Require(ConvertStringSecurityDescriptorToSecurityDescriptorW(L"D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;CCLC;;;AU)", SDDL_REVISION_1, &descriptor, nullptr), "Create service ACL");
    LocalMemory memory; memory.value = descriptor;
    Require(SetServiceObjectSecurity(service.value, DACL_SECURITY_INFORMATION, descriptor), "Protect service control");
}
void RemoveService(SC_HANDLE manager, const fs::path& root, const std::wstring& id) {
    Stop(manager, root, id);
    {
        ServiceHandle service(OpenServiceW(manager, ServiceName, DELETE));
        if (!service.value && GetLastError() == ERROR_SERVICE_DOES_NOT_EXIST) return;
        Require(service.value != nullptr && DeleteService(service.value), "Remove stopped core service");
    }
    const auto deadline = GetTickCount64() + 10000;
    while (true) {
        ServiceHandle service(OpenServiceW(manager, ServiceName, SERVICE_QUERY_STATUS));
        if (!service.value && GetLastError() == ERROR_SERVICE_DOES_NOT_EXIST) return;
        if (GetTickCount64() >= deadline) Fail("Service deletion is pending; files retained", ERROR_TIMEOUT);
        Sleep(100);
    }
}
struct Transaction { std::wstring next; State previous; };
fs::path Journal(const fs::path& root) { return root / L"setup-pending.txt"; }
void Save(const fs::path& root, const Transaction& transaction) {
    const std::wstring data = L"weave-setup-v2\n" + transaction.next + L"\n" + (transaction.previous.exists ? transaction.previous.id : L"-") + L"\n" +
        std::to_wstring(transaction.previous.start) + L"\n" + (transaction.previous.running ? L"1\n" : L"0\n");
    const auto temp = root / L"setup-pending.new";
    Handle file(CreateFileW(temp.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr));
    Require(file.value != INVALID_HANDLE_VALUE, "Create setup recovery record");
    DWORD written = 0; const auto bytes = static_cast<DWORD>(data.size() * sizeof(wchar_t));
    Require(WriteFile(file.value, data.data(), bytes, &written, nullptr) && written == bytes && FlushFileBuffers(file.value), "Persist setup recovery record");
    Require(CloseHandle(file.value), "Close setup recovery record"); file.value = INVALID_HANDLE_VALUE;
    Require(MoveFileExW(temp.c_str(), Journal(root).c_str(), MOVEFILE_WRITE_THROUGH), "Publish setup recovery record");
}
Transaction Read(const fs::path& root) {
    CheckEntry(Journal(root));
    Handle file(CreateFileW(Journal(root).c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr));
    Require(file.value != INVALID_HANDLE_VALUE, "Open setup recovery record");
    LARGE_INTEGER size{};
    Require(GetFileSizeEx(file.value, &size) && size.QuadPart > 0 && size.QuadPart <= 1024 && size.QuadPart % 2 == 0, "Invalid setup recovery size");
    std::wstring data(static_cast<size_t>(size.QuadPart / 2), L'\0'); DWORD read = 0;
    Require(ReadFile(file.value, data.data(), static_cast<DWORD>(size.QuadPart), &read, nullptr) && read == static_cast<DWORD>(size.QuadPart), "Read setup recovery record");
    std::vector<std::wstring> lines; size_t start = 0;
    for (size_t at = 0; (at = data.find(L'\n', start)) != std::wstring::npos; start = at + 1) lines.push_back(data.substr(start, at - start));
    if (lines.size() != 5 || data.back() != L'\n' || lines[0] != L"weave-setup-v2" || !ReleaseId(lines[1]) ||
        (lines[2] != L"-" && !ReleaseId(lines[2])) || (lines[3] != L"2" && lines[3] != L"3") || (lines[4] != L"0" && lines[4] != L"1"))
        Fail("Damaged setup recovery record; original files retained", ERROR_INVALID_DATA);
    return {lines[1], {lines[2] != L"-", lines[2], static_cast<DWORD>(lines[3][0] - L'0'), lines[4] == L"1"}};
}
void Archive(const fs::path& root, const Transaction& transaction, const wchar_t* result) {
    CreateProtected(root / L"setup-history");
    auto target = root / L"setup-history" / (transaction.next + L"-" + result + L".txt");
    Require(MoveFileExW(Journal(root).c_str(), target.c_str(), MOVEFILE_WRITE_THROUGH), "Archive setup recovery record");
}
void Rollback(SC_HANDLE manager, const fs::path& root) {
    if (!fs::exists(Journal(root))) return;
    const auto transaction = Read(root); const auto current = InspectService(manager, root, false);
    if (current.exists && current.id != transaction.next && (!transaction.previous.exists || current.id != transaction.previous.id))
        Fail("Service changed during setup; recovery record retained", ERROR_ACCESS_DENIED);
    if (current.exists && current.id == transaction.next) {
        Stop(manager, root, current.id);
        if (transaction.previous.exists) Configure(manager, root, transaction.previous.id, transaction.previous.start, false);
        else RemoveService(manager, root, current.id);
    }
    else if (!current.exists && transaction.previous.exists)
        Configure(manager, root, transaction.previous.id, transaction.previous.start, true);
    if (transaction.previous.exists && transaction.previous.running) Start(manager, root, transaction.previous.id);
    RestoreShortcuts(root, transaction.next, transaction.previous);
    Archive(root, transaction, L"rolled-back");
}
void RequireClosedGui(const fs::path& root) {
    Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0));
    Require(snapshot.value != INVALID_HANDLE_VALUE, "Inspect running application versions");
    PROCESSENTRY32W entry{}; entry.dwSize = sizeof entry;
    if (!Process32FirstW(snapshot.value, &entry)) Fail("Enumerate running applications");
    do {
        if (_wcsicmp(entry.szExeFile, L"Weave.Windows.exe") != 0) continue;
        Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, entry.th32ProcessID));
        if (!process.value) {
            if (GetLastError() == ERROR_INVALID_PARAMETER) continue;
            Fail("Cannot verify a running Weave process; close it before retrying");
        }
        wchar_t image[32768]; DWORD length = 32768;
        Require(QueryFullProcessImageNameW(process.value, 0, image, &length), "Read running application path");
        const auto prefix = (root / L"versions").wstring() + L"\\";
        if (_wcsnicmp(image, prefix.c_str(), prefix.size()) == 0)
            Fail("Close Weave in every user session, then retry setup", ERROR_INSTALL_ALREADY_RUNNING);
    } while (Process32NextW(snapshot.value, &entry));
    if (GetLastError() != ERROR_NO_MORE_FILES) Fail("Application enumeration was interrupted");
}
void Execute(const std::wstring& command, const fs::path& root, const std::wstring& id, SC_HANDLE manager) {
    if (command == L"inspect") { InspectService(manager, root); return; }
    if (command == L"begin") {
        CreateProtected(root); CreateProtected(root / L"versions");
        Rollback(manager, root);
        if (fs::exists(root / L"setup-pending.new")) Fail("An incomplete setup record requires recovery; files retained", ERROR_INVALID_DATA);
        const auto previous = InspectService(manager, root);
        const auto next = root / L"versions" / id;
        if (fs::exists(next)) Fail("This staged release already exists", ERROR_ALREADY_EXISTS);
        CreateProtected(next);
        SnapshotShortcuts(root, id, previous);
        Save(root, {id, previous});
        if (previous.exists) Stop(manager, root, previous.id);
        return;
    }
    if (command == L"uninstall") {
        RequireClosedGui(root);
        if (fs::exists(Journal(root))) Fail("Recover the pending installation before uninstalling", ERROR_INSTALL_SUSPEND);
        const auto current = InspectService(manager, root);
        if (current.exists) RemoveService(manager, root, current.id);
        return;
    }
    if (command == L"rollback") {
        if (fs::exists(Journal(root)) && Read(root).next != id) Fail("Another setup owns the recovery record", ERROR_ACCESS_DENIED);
        Rollback(manager, root); return;
    }
    const auto transaction = Read(root);
    if (transaction.next != id) Fail("A different setup transaction owns this installation", ERROR_ACCESS_DENIED);
    if (command == L"activate") {
        RequireClosedGui(root);
        const auto release = root / L"versions" / id;
        CheckTree(release);
        Require(fs::is_regular_file(Image(root, id)) && fs::is_regular_file(release / L"Weave.Windows.exe"), "Incomplete staged release");
        const auto current = InspectService(manager, root);
        if (current.exists && (!transaction.previous.exists || current.id != transaction.previous.id)) Fail("Existing service changed during installation", ERROR_ACCESS_DENIED);
        Configure(manager, root, id, SERVICE_AUTO_START, !current.exists);
        Start(manager, root, id);
    }
    else if (command == L"commit") {
        const auto current = InspectService(manager, root);
        if (!current.exists || current.id != id || !current.running) Fail("The new service is not running", ERROR_SERVICE_NOT_ACTIVE);
        for (const auto& path : Shortcuts()) {
            CheckAncestors(path);
            if (!Same(ShortcutTarget(path), Gui(root, id).wstring())) Fail("The installed shortcut was not published", ERROR_INVALID_DATA);
        }
        Archive(root, transaction, L"committed");
    }
    else Fail("Unknown setup operation", ERROR_INVALID_PARAMETER);
}
}
int wmain(int argc, wchar_t** argv) {
    SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_SYSTEM32);
    try {
        if (argc != 4 || (std::wstring(argv[1]) != L"inspect" && std::wstring(argv[1]) != L"uninstall" && !ReleaseId(argv[3])))
            Fail("Invalid setup arguments", ERROR_INVALID_PARAMETER);
        BYTE admins[SECURITY_MAX_SID_SIZE]; DWORD size = sizeof admins; BOOL elevated = FALSE;
        Require(CreateWellKnownSid(WinBuiltinAdministratorsSid, nullptr, admins, &size) && CheckTokenMembership(nullptr, admins, &elevated) && elevated,
            "Administrator authorization is required");
        const HRESULT com = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
        if (FAILED(com)) Fail("Initialize shell link reader", static_cast<DWORD>(com));
        struct ComScope { ~ComScope() { CoUninitialize(); } } comScope;
        const auto root = MachineRoot(argv[2]);
        ServiceHandle manager(OpenSCManagerW(nullptr, nullptr, SC_MANAGER_CONNECT | SC_MANAGER_CREATE_SERVICE));
        Require(manager.value != nullptr, "Open service manager");
        Execute(argv[1], root, argv[3], manager.value);
        std::wcout << L"Weave setup " << argv[1] << L" completed.\n";
        return 0;
    }
    catch (const std::exception& error) { std::cerr << "Weave setup: " << error.what() << '\n'; return 1; }
}
