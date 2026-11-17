using System;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace Yoinks.Services;

// Keeps a page's content column centered and sized to the window.
// (A stretched panel with MaxWidth gets centered by its natural width in WinUI,
// so the width is set explicitly instead.)
public static class ResponsiveLayout
{
    public const double MaxContentWidth = 980;

    public static void Attach(ScrollViewer scroller, FrameworkElement content, Action<double>? onWidth = null, double maxWidth = MaxContentWidth)
    {
        content.HorizontalAlignment = HorizontalAlignment.Center;
        content.MaxWidth = double.PositiveInfinity;

        void Update()
        {
            double available = scroller.ViewportWidth > 0 ? scroller.ViewportWidth : scroller.ActualWidth;
            if (available <= 0) return;

            double width = Math.Min(available, maxWidth);
            content.Width = width;

            double side = available < 600 ? 16 : available < 900 ? 24 : 36;
            if (content is StackPanel sp)
                sp.Padding = new Thickness(side, 20, side, 36);

            onWidth?.Invoke(width - side * 2);
        }

        scroller.SizeChanged += (_, _) => Update();
        scroller.Loaded += (_, _) => Update();
    }
}
