using System.Linq;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;

namespace Yoinks.Controls;

// The yoinks pixel wordmark (from the original project's logo.svg),
// drawn as rounded glass tiles with the brand gradient running through them.
public sealed class PixelLogo : Canvas
{
    // '#' solid cell, '+' shaded (half) cell, '.' empty
    private static readonly string[] Rows =
    {
        "+.+.###.###.##.#.#.#.###",
        "+.+.#.#..#..#.##.#.#.#..",
        "###.#.+..+..#..+.+#..##+",
        ".#..#.+..+..#..+.+.#...+",
        ".#..###.###.#..#.#.#.###",
    };

    public static readonly DependencyProperty CellSizeProperty =
        DependencyProperty.Register(nameof(CellSize), typeof(double), typeof(PixelLogo), new PropertyMetadata(10.0, (d, _) => ((PixelLogo)d).Build()));

    public double CellSize
    {
        get => (double)GetValue(CellSizeProperty);
        set => SetValue(CellSizeProperty, value);
    }

    public PixelLogo()
    {
        Loaded += (_, _) => Build();
    }

    private void Build()
    {
        Children.Clear();
        double s = CellSize;
        double gap = System.Math.Max(1, s * 0.12);
        Width = Rows[0].Length * s;
        Height = Rows.Length * s;

        // one gradient across the whole word: each tile takes the colour at its position
        // (brush coordinates in XAML are per element, so a shared brush would restart on every tile)
        var stops = (Application.Current.Resources["BrandGradient"] as LinearGradientBrush)?.GradientStops
                        .OrderBy(g => g.Offset).Select(g => (g.Offset, g.Color)).ToArray()
                    ?? new[] { (0.0, Microsoft.UI.Colors.HotPink), (1.0, Microsoft.UI.Colors.DodgerBlue) };
        int cols = Rows[0].Length;

        for (int r = 0; r < Rows.Length; r++)
        {
            for (int c = 0; c < Rows[r].Length; c++)
            {
                char ch = Rows[r][c];
                if (ch == '.') continue;
                double t = (c + r * 0.6) / (cols - 1 + Rows.Length * 0.6);
                var rect = new Rectangle
                {
                    Width = s - gap,
                    Height = s - gap,
                    RadiusX = s * 0.22,
                    RadiusY = s * 0.22,
                    Fill = new SolidColorBrush(ColorAt(stops, t)),
                    Opacity = ch == '+' ? 0.38 : 1,
                };
                SetLeft(rect, c * s);
                SetTop(rect, r * s);
                Children.Add(rect);
            }
        }
    }

    private static Windows.UI.Color ColorAt((double Offset, Windows.UI.Color Color)[] stops, double t)
    {
        t = System.Math.Clamp(t, 0, 1);
        for (int i = 1; i < stops.Length; i++)
        {
            if (t > stops[i].Offset) continue;
            var a = stops[i - 1]; var b = stops[i];
            double k = b.Offset > a.Offset ? (t - a.Offset) / (b.Offset - a.Offset) : 0;
            byte L(byte x, byte y) => (byte)(x + (y - x) * k);
            return Windows.UI.Color.FromArgb(255, L(a.Color.R, b.Color.R), L(a.Color.G, b.Color.G), L(a.Color.B, b.Color.B));
        }
        return stops[^1].Color;
    }
}
