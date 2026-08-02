import type { NextConfig } from "next";
const apiOrigin=process.env.API_ORIGIN||"http://localhost:8080";
const nextConfig: NextConfig = {
  reactStrictMode: true,
  async rewrites(){return [{source:"/backend/:path*",destination:`${apiOrigin}/:path*`}];}
};
export default nextConfig;
