import type { Metadata } from "next";
import "./styles.css";
import "./console-shell.css";

export const metadata: Metadata = {
  title: "Cosmic Database Console",
  description: "Visual administration for Cosmic MapleStory",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
