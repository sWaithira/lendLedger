package com.lendLedger.backend.security;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** DEV ONLY and TEMPORARY: a bare-bones login page until the React app has its own. Delete later. */
@Profile("dev")
@RestController
class DevLoginController {

    @GetMapping(value = "/dev-login", produces = MediaType.TEXT_HTML_VALUE)
    String page() {
        return PAGE;
    }

    private static final String PAGE = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><title>LendLedger dev login</title>
            <style>
              body{font-family:system-ui,sans-serif;max-width:640px;margin:2rem auto;padding:0 1rem}
              input{display:block;margin:.4rem 0;padding:.5rem;width:100%;box-sizing:border-box}
              button{padding:.5rem 1rem;margin:.3rem .3rem 0 0}
              pre{background:#f4f4f4;padding:1rem;overflow:auto;white-space:pre-wrap}
            </style></head>
            <body>
            <h2>LendLedger: dev login (temporary)</h2>
            <form id="f">
              <input name="email" value="owner@demo.lendledger.test" autocomplete="username">
              <input name="password" type="password" value="Demo#2026pass" autocomplete="current-password">
              <button type="submit">Sign in</button>
              <button type="button" id="me">GET /api/v1/me</button>
              <button type="button" id="probe">GET /api/v1/dev/owner-only</button>
              <button type="button" id="out">Sign out</button>
            </form>
            <pre id="o">Not signed in.</pre>
            <script>
              const o = document.getElementById('o');
              const f = document.getElementById('f');
              const xsrf = () => decodeURIComponent(
                (document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN=')) || '').split('=')[1] || '');

              async function call(method, url, body) {
                const headers = {'X-XSRF-TOKEN': xsrf()};
                if (body) headers['Content-Type'] = 'application/x-www-form-urlencoded';
                const res = await fetch(url, {method, headers, body, credentials: 'same-origin'});
                const text = await res.text();
                o.textContent = method + ' ' + url + '\\n-> ' + res.status + '\\n\\n' + text;
                return res;
              }

              f.addEventListener('submit', async e => {
                e.preventDefault();
                const res = await call('POST', '/api/v1/auth/login', new URLSearchParams(new FormData(f)));
                if (res.ok) await call('GET', '/api/v1/me');   // also refreshes the CSRF cookie after login
              });
              document.getElementById('me').onclick = () => call('GET', '/api/v1/me');
              document.getElementById('probe').onclick = () => call('GET', '/api/v1/dev/owner-only');
              document.getElementById('out').onclick = () => call('POST', '/api/v1/auth/logout');
            </script>
            </body></html>
            """;
}