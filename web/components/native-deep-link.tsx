"use client";
import {useEffect} from "react";
import {App} from "@capacitor/app";
import {Capacitor} from "@capacitor/core";
export function NativeDeepLink(){useEffect(()=>{if(!Capacitor.isNativePlatform())return;const listener=App.addListener("appUrlOpen",({url})=>{const callback=new URL(url);if(callback.protocol!=="toogeduler:")return;const appUrl=process.env.NEXT_PUBLIC_APP_URL||"https://app.toogeduler.com";window.location.assign(`${appUrl}/${callback.host}${callback.pathname}${callback.search}`);});return ()=>{listener.then(handle=>handle.remove());};},[]);return null;}
