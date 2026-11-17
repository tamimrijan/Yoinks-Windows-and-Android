using System;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media.Imaging;
using Yoinks.Services;

namespace Yoinks.Pages;

public sealed partial class AboutPage : Page
{
    public AboutPage()
    {
        InitializeComponent();
        ResponsiveLayout.Attach(Scroller, ContentPanel);
        var v = typeof(AboutPage).Assembly.GetName().Version;
        if (v != null) VersionText.Text = $"Version {v.Major}.{v.Minor}.{v.Build}";

        // GitHub avatar; when offline the PersonPicture just shows the initials
        Avatar.ProfilePicture = new BitmapImage(new Uri("https://github.com/tamimrijan.png?size=200"));
    }

    private static async void Open(string url) => await Windows.System.Launcher.LaunchUriAsync(new Uri(url));

    private void Instagram_Click(object sender, RoutedEventArgs e) => Open("https://www.instagram.com/tamim_ahmed_rijan/");

    private void GitHub_Click(object sender, RoutedEventArgs e) => Open("https://github.com/tamimrijan");
}
