import type { Metadata, Viewport } from "next";
import { Nunito } from "next/font/google";
import "./globals.css";
import { Providers } from "@/components/Providers";

// Nunito is a rounded geometric sans — the closest open web font to the native
// app's rounded brand font.
const rounded = Nunito({
  subsets: ["latin"],
  variable: "--font-rounded",
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL("https://muthal-web.vercel.app"),
  title: "Muthal",
  description: "Income & expense tracking for institutions.",
  manifest: "/manifest.json",
  appleWebApp: {
    capable: true,
    title: "Muthal",
    statusBarStyle: "default",
  },
  icons: {
    icon: "/icon-192.png",
    apple: "/apple-touch-icon.png",
  },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#fafafa" },
    { media: "(prefers-color-scheme: dark)", color: "#121212" },
  ],
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  viewportFit: "cover",
};

// Apply the saved theme before paint to avoid a flash of the wrong scheme.
const themeInit = `(function(){try{var t=localStorage.getItem('appearance_mode')||'system';var f=localStorage.getItem('use_google_font');var r=document.documentElement;r.setAttribute('data-theme',t);r.setAttribute('data-font',f==='false'?'system':'rounded');}catch(e){}})();`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      data-theme="system"
      data-font="rounded"
      className={`${rounded.variable} h-full`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInit }} />
      </head>
      <body className="min-h-full antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
