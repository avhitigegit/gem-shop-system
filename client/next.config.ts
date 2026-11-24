import type { NextConfig } from "next";

// Where /api goes when the frontend runs on its own (npm run dev).
// In Docker Compose, Nginx sends /api to the backend before it reaches Next.js.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8081";

const nextConfig: NextConfig = {
  output: "standalone", // small Docker image (node server.js)
  poweredByHeader: false, // do not tell visitors which framework runs the site
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  // The push service worker (P4-02) must never be cached, so a new version reaches the phones straight away.
  async headers() {
    return [
      // P6-01 security check: safe browser settings on every page (the backend sets its own on /api).
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          {
            key: "Content-Security-Policy",
            value: "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'",
          },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          // the camera is used for gem, purchase and expense photos; nothing else
          { key: "Permissions-Policy", value: "camera=(self), microphone=(), geolocation=(), payment=()" },
        ],
      },
      {
        source: "/sw.js",
        headers: [
          { key: "Content-Type", value: "application/javascript; charset=utf-8" },
          { key: "Cache-Control", value: "no-cache, no-store, must-revalidate" },
          { key: "Content-Security-Policy", value: "default-src 'self'; script-src 'self'" },
        ],
      },
    ];
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
