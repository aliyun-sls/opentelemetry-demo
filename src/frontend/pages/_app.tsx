// Copyright The OpenTelemetry Authors
// SPDX-License-Identifier: Apache-2.0

import '../styles/globals.css';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App, { AppContext, AppProps } from 'next/app';
import { getCookie } from 'cookies-next';
import CurrencyProvider from '../providers/Currency.provider';
import CartProvider from '../providers/Cart.provider';
import { ThemeProvider } from 'styled-components';
import Theme from '../styles/Theme';
import FrontendTracer from '../utils/telemetry/FrontendTracer';
import SessionGateway from '../gateways/Session.gateway';
import { OpenFeatureProvider, OpenFeature } from '@openfeature/react-sdk';
import { FlagdWebProvider } from '@openfeature/flagd-web-provider';
import Script from "next/script";

declare global {
  interface Window {
    ENV: {
      NEXT_PUBLIC_PLATFORM?: string;
      NEXT_PUBLIC_OTEL_SERVICE_NAME?: string;
      NEXT_PUBLIC_OTEL_EXPORTER_OTLP_TRACES_ENDPOINT?: string;
      IS_SYNTHETIC_REQUEST?: string;
      ALIBABA_CLOUD_RUM_PID?: string;
      ALIBABA_CLOUD_RUM_ENDPOINT?: string;
    };
  }
}

let alibabaCloudRumConfig = `
window.__rum = {
  "pid": "",
  "endpoint": "",
  "tracing": true
};
`


if (typeof window !== 'undefined') {
  const collector = getCookie('otelCollectorUrl')?.toString() || '';
  FrontendTracer(collector);
  if (window.location) {
    const session = SessionGateway.getSession();

    // Set context prior to provider init to avoid multiple http calls
    OpenFeature.setContext({ targetingKey: session.userId, ...session }).then(() => {
      /**
       * We connect to flagd through the envoy proxy, straight from the browser,
       * for this we need to know the current hostname and port.
       */

      const useTLS = window.location.protocol === 'https:';
      let port = useTLS ? 443 : 80;
      if (window.location.port) {
          port = parseInt(window.location.port, 10);
      }

      OpenFeature.setProvider(
        new FlagdWebProvider({
          host: window.location.hostname,
          pathPrefix: 'flagservice',
          port: port,
          tls: useTLS,
          maxRetries: 3,
          maxDelay: 10000,
        })
      );
    });
  }

  alibabaCloudRumConfig = `
    window.__rum = {
      "pid": "${window.ENV.ALIBABA_CLOUD_RUM_PID}",
      "endpoint": "${window.ENV.ALIBABA_CLOUD_RUM_ENDPOINT}",
      "tracing": true
    };
    `
}

const queryClient = new QueryClient();

function MyApp({ Component, pageProps }: AppProps) {
  const children = <ThemeProvider theme={Theme}>
    <OpenFeatureProvider>
      <QueryClientProvider client={queryClient}>
        <CurrencyProvider>
          <CartProvider>
            <Component {...pageProps} />
          </CartProvider>
        </CurrencyProvider>
      </QueryClientProvider>
    </OpenFeatureProvider>
  </ThemeProvider>

  return (
      <html lang="en">
      <Script id="arms-rum" type="text/javascript">
        {alibabaCloudRumConfig}
      </Script>
      <body>
      <Script type="text/javascript" src="https://sdk.rum.aliyuncs.com/v2/browser-sdk.js" crossOrigin='anonymous'/>
      {children}
      </body>
      </html>
  );
}

MyApp.getInitialProps = async (appContext: AppContext) => {
  const appProps = await App.getInitialProps(appContext);

  return { ...appProps };
};

export default MyApp;
