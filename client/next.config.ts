import type { NextConfig } from "next";

// Where /api goes when the frontend runs on its own (npm run dev).
// In Docker Compose, Nginx sends /api to the backend before it reaches Next.js.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8081";

const nextConfig: NextConfig = {
  output: "standalone", // small Docker image (node server.js)
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
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
