using System.Diagnostics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Weave.Windows.Core;
using Windows.System;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private const int CustomDnsIndex = 3;

    private void LoadNetworkOptionsIntoControls()
    {
        var options = _model.NetworkOptions;
        DnsProfileComboBox.SelectedIndex = options.DnsProfile switch
        {
            DnsProfile.AdBlock => 1,
            DnsProfile.Family => 2,
            DnsProfile.Custom => CustomDnsIndex,
            _ => 0,
        };
        CustomDnsEndpointBox.Text = options.CustomDnsEndpoint ?? string.Empty;
        CustomDnsRow.Visibility = Show(DnsProfileComboBox.SelectedIndex == CustomDnsIndex);
        Ipv6ToggleSwitch.IsOn = options.Ipv6Enabled;
        BlockStunToggleSwitch.IsOn = options.BlockUdpStun;
        UpdateNetworkChips();
    }

    private void DnsProfileComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        var custom = DnsProfileComboBox.SelectedIndex == CustomDnsIndex;
        CustomDnsRow.Visibility = Show(custom);
        if (custom && string.IsNullOrWhiteSpace(CustomDnsEndpointBox.Text))
        {
            // Nothing valid to save yet; keep the previous profile active until an address is entered.
            SetCustomDnsHint("输入 DoH / DoT 地址后自动保存；在此之前仍使用原 DNS 方案。", isError: false);
            CustomDnsEndpointBox.Focus(FocusState.Programmatic);
            return;
        }

        SaveNetworkOptions();
    }

    private void NetworkToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_initialized) SaveNetworkOptions();
    }

    private void CustomDnsEndpointBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key != VirtualKey.Enter) return;
        e.Handled = true;
        SaveNetworkOptions();
    }

    private void CustomDnsEndpointBox_LostFocus(object sender, RoutedEventArgs e)
    {
        if (_initialized && DnsProfileComboBox.SelectedIndex == CustomDnsIndex &&
            CustomDnsEndpointBox.Text.Trim() != (_model.NetworkOptions.CustomDnsEndpoint ?? string.Empty))
        {
            SaveNetworkOptions();
        }
    }

    private void SaveNetworkOptions()
    {
        var options = new WindowsNetworkOptions
        {
            EnableTun = true,
            Ipv6Enabled = Ipv6ToggleSwitch.IsOn,
            BlockUdpStun = BlockStunToggleSwitch.IsOn,
            DnsProfile = DnsProfileComboBox.SelectedIndex switch
            {
                1 => DnsProfile.AdBlock,
                2 => DnsProfile.Family,
                CustomDnsIndex => DnsProfile.Custom,
                _ => DnsProfile.Privacy,
            },
            CustomDnsEndpoint = CustomDnsEndpointBox.Text.Trim(),
        };

        try
        {
            _model.SaveNetworkOptions(options);
        }
        catch (Exception exception)
        {
            if (options.DnsProfile == DnsProfile.Custom)
            {
                SetCustomDnsHint($"{exception.Message}；尚未保存。", isError: true);
            }
            else
            {
                Notify(exception.Message, InfoBarSeverity.Error, "无法保存网络设置");
            }

            return;
        }

        SetCustomDnsHint("支持 https:// DoH 或 tls:// DoT；按 Enter 或离开输入框时保存。", isError: false);
        NetworkSavedText.Text = $"已自动保存 · {DateTime.Now:HH:mm:ss}。更改在下次连接时生效。";
        UpdateNetworkChips();
        MarkDirty("网络设置");
    }

    private void SetCustomDnsHint(string text, bool isError)
    {
        CustomDnsHintText.Text = text;
        CustomDnsHintText.Foreground = (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources[
            isError ? "SystemFillColorCriticalBrush" : "TextFillColorSecondaryBrush"];
    }

    private void UpdateAboutSection()
    {
        var core = WeaveAppModel.MihomoPath;
        CorePathText.Text = core ?? "未找到 mihomo.exe。请放到应用目录的 runtime 文件夹，或设置 WEAVE_MIHOMO_PATH。";
        CoreStateText.Text = core is null ? "缺失" : "已就绪";
        DataPathText.Text = _model.DataDirectory;
    }

    private void OpenDataFolderButton_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            Directory.CreateDirectory(_model.DataDirectory);
            Process.Start(new ProcessStartInfo("explorer.exe", $"\"{_model.DataDirectory}\"") { UseShellExecute = true })?.Dispose();
        }
        catch (Exception exception)
        {
            Notify(exception.Message, InfoBarSeverity.Error, "无法打开文件夹");
        }
    }
}
