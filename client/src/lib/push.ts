"use client";

/* Push alerts on this phone / browser (P4-02).
   - registerServiceWorker: every page load; the worker (public/sw.js) shows pushes and opens the screen on tap.
   - turnOnPush: asks permission, signs this browser up with the shop's VAPID key and tells the server.
   - turnOffPush: signs out of push here and tells the server.
   iPhone: push only works after "Add to Home Screen" (iOS 16.4 or later), opened from the home-screen icon. */
import { api } from "@/lib/api/client";
import type { components } from "@/lib/api/schema";

export type PushSettings = components["schemas"]["PushSettings"];
export type PushKind = components["schemas"]["PushKindSetting"]["kind"];

export type PushState =
  | "unsupported" // this browser cannot do push
  | "needs-install" // iPhone / iPad in Safari: add to the home screen first
  | "blocked" // the person said no – change it in the phone / browser settings
  | "off"
  | "on";

const supported = () =>
  typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;

const isIos = () => typeof navigator !== "undefined" && /iPhone|iPad|iPod/.test(navigator.userAgent);
const standalone = () =>
  typeof window !== "undefined" &&
  (window.matchMedia?.("(display-mode: standalone)").matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true);

export async function registerServiceWorker() {
  if (typeof window === "undefined" || !("serviceWorker" in navigator)) return null;
  try {
    return await navigator.serviceWorker.register("/sw.js", { scope: "/", updateViaCache: "none" });
  } catch {
    return null;
  }
}

async function subscription() {
  if (!supported()) return null;
  const reg = (await navigator.serviceWorker.getRegistration("/")) ?? (await registerServiceWorker());
  return reg ? reg.pushManager.getSubscription() : null;
}

export async function pushState(): Promise<PushState> {
  if (!supported()) return isIos() && !standalone() ? "needs-install" : "unsupported";
  if (Notification.permission === "denied") return "blocked";
  const sub = await subscription();
  return sub && Notification.permission === "granted" ? "on" : "off";
}

/** The device name the owner sees in the list, e.g. "iPhone · Safari". */
function deviceName() {
  const ua = navigator.userAgent;
  const os = /iPhone/.test(ua)
    ? "iPhone"
    : /iPad/.test(ua)
      ? "iPad"
      : /Android/.test(ua)
        ? "Android phone"
        : /Windows/.test(ua)
          ? "Windows computer"
          : /Mac OS X/.test(ua)
            ? "Mac"
            : "Computer";
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /Firefox\//.test(ua)
      ? "Firefox"
      : /Chrome\//.test(ua)
        ? "Chrome"
        : /Safari\//.test(ua)
          ? "Safari"
          : "Browser";
  return `${os} · ${browser}`;
}

function keyBytes(b64: string) {
  const s = atob(b64.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (b64.length % 4)) % 4));
  const out = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}

/** Returns an error message, or null when push is on. */
export async function turnOnPush(): Promise<string | null> {
  if (!supported())
    return isIos()
      ? "On iPhone, first tap Share → Add to Home Screen, then open the app from the home screen."
      : "This browser cannot show push alerts.";
  const permission = await Notification.requestPermission();
  if (permission !== "granted") return "Notifications were not allowed. Allow them in the phone settings for this app.";
  const reg = await registerServiceWorker();
  if (!reg) return "The app could not start its background helper. Reload the page and try again.";
  await navigator.serviceWorker.ready;
  const { data: key } = await api.GET("/api/push/key");
  if (!key) return "Cannot reach the server. Check the connection and try again.";
  let sub = await reg.pushManager.getSubscription();
  const want = keyBytes(key.publicKey);
  const has = sub?.options.applicationServerKey ? new Uint8Array(sub.options.applicationServerKey) : null;
  if (sub && (!has || has.length !== want.length || has.some((b, i) => b !== want[i]))) {
    await sub.unsubscribe(); // signed up with another key before
    sub = null;
  }
  try {
    sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: want });
  } catch {
    return "The phone did not accept the push sign-up. Try again, or check the notification settings.";
  }
  const j = sub.toJSON();
  const { error } = await api.POST("/api/push/devices", {
    body: { endpoint: sub.endpoint, p256dh: j.keys?.p256dh ?? "", auth: j.keys?.auth ?? "", device: deviceName() },
  });
  if (error) {
    await sub.unsubscribe();
    return "The server did not accept this phone. Please try again.";
  }
  return null;
}

export async function turnOffPush() {
  const sub = await subscription();
  if (!sub) return;
  await api.DELETE("/api/push/devices", { params: { query: { endpoint: sub.endpoint } } });
  await sub.unsubscribe();
}
