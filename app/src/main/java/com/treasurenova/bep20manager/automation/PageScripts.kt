package com.treasurenova.bep20manager.automation

import com.treasurenova.bep20manager.logic.Safety

object PageScripts {
    val library: String = """
        (function(){
          if (window.__tn) return;
          function text(el){ return ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g,' ').trim(); }
          function forbidden(t){
            var s = (t || '').toLowerCase();
            var words = ['withdraw','withdrawal','transfer','trade','swap','sell','pay 30','pay ','complete verification','complete'];
            for (var i=0;i<words.length;i++){ if (s.indexOf(words[i]) >= 0) return true; }
            return false;
          }
          function setVal(el, value){
            var proto = Object.getPrototypeOf(el);
            var desc = Object.getOwnPropertyDescriptor(proto, 'value');
            if (!desc) desc = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
            desc.set.call(el, value);
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
          }
          function safeLoginLabel(label){
            var v = (label || '').replace(/\s+/g,' ').trim().toLowerCase();
            return v === 'login' || v === 'log in' || v === 'sign in';
          }
          function clickExact(wanted){
            if (forbidden(wanted) || !window.__tn.allow(wanted)) return false;
            var nodes = document.querySelectorAll('button,a,li,span,div');
            for (var i=0;i<nodes.length;i++){
              var t = text(nodes[i]);
              if (!t || t.length > 48) continue;
              if (forbidden(t)) continue;
              if (t.toLowerCase() === wanted.toLowerCase()){ nodes[i].click(); return true; }
            }
            return false;
          }
          function bodyText(){ return text(document.body).slice(0, 4000); }
          function blocked(){
            var b = bodyText().toLowerCase();
            var marks = ['not available in your region','not available in your country','service is not available','access denied','geographic restriction','legal restriction','unusual traffic','attention required','verify you are human','bot detection'];
            for (var i=0;i<marks.length;i++){
              var at = b.indexOf(marks[i]);
              if (at >= 0) return bodyText().slice(Math.max(0, at-20), at+140);
            }
            return '';
          }
          function twoFactor(){
            var b = bodyText().toLowerCase();
            return b.indexOf('authenticator') >= 0 || b.indexOf('two-factor') >= 0 || b.indexOf('2fa') >= 0 || b.indexOf('enter the 6') >= 0 || b.indexOf('google verification code') >= 0;
          }
          function loginError(){
            var b = bodyText().toLowerCase();
            var marks = ['incorrect password','invalid password','wrong password','login failed','account or password'];
            for (var i=0;i<marks.length;i++){ if (b.indexOf(marks[i]) >= 0) return marks[i]; }
            return '';
          }
          function loginRoot(){
            var pass = document.querySelector('input[type="password"]');
            if (!pass) return null;
            var form = pass.closest ? pass.closest('form') : null;
            if (form) return form;
            var node = pass.parentElement;
            if (node && node.parentElement && node.parentElement.tagName !== 'BODY') return node.parentElement;
            return node;
          }
          function collectLoginControls(){
            var root = loginRoot();
            var action = '';
            if (root && root.tagName === 'FORM') action = root.getAttribute('action') || '';
            var nodes = document.querySelectorAll('button, input[type="submit"], input[type="button"]');
            var els = [];
            var out = [];
            for (var i=0;i<nodes.length;i++){
              var el = nodes[i];
              var tag = el.tagName.toLowerCase();
              var type = (el.getAttribute('type') || '').toLowerCase();
              var label = tag === 'input' ? (el.value || '') : text(el);
              label = (label || '').replace(/\s+/g,' ').trim();
              if (!label || label.length > 48) continue;
              var inLogin = !!(root && root.contains && root.contains(el));
              var submit = inLogin && (type === 'submit' || (tag === 'button' && (type === '' || type === 'submit')));
              els.push(el);
              out.push({index: out.length, text: label, tag: tag, type: type, inLoginForm: inLogin, submitControl: submit, formAction: inLogin ? action : ''});
            }
            window.__tnLoginEls = els;
            return out;
          }
          function depositBlocks(){
            var nodes = document.querySelectorAll('section, article, li, tr, div, p');
            var out = [];
            var seen = {};
            for (var i=0;i<nodes.length;i++){
              var block = text(nodes[i]);
              if (!block || block.length > 800) continue;
              if (!/usdt|bep-?20|bnb|trc|tron|0x/i.test(block)) continue;
              if (seen[block]) continue;
              seen[block] = 1;
              out.push(block);
              if (out.length >= 40) break;
            }
            return out;
          }
          function pageState(){
            return {
              loginFieldsFound: !!document.querySelector('input[type="password"]'),
              blockedMessage: blocked(),
              twoFactorVisible: twoFactor(),
              loginError: loginError(),
              depositBlocks: depositBlocks(),
              loggedOut: false,
              authenticatedUiVisible: false
            };
          }
          window.__tn = {
            allow: function(label){ return !forbidden(label); },
            listLoginControls: function(){
              return JSON.stringify(collectLoginControls());
            },
            fillLogin: function(user, pass, index){
              var state = pageState();
              var userEl = document.querySelector('input[placeholder="Username/Email"]') || document.querySelector('input[placeholder*="Username" i]');
              var passEl = document.querySelector('input[placeholder="Password"]') || document.querySelector('input[type="password"]');
              state.loginFieldsFound = !!(userEl && passEl);
              state.loginSubmitted = false;
              if (!userEl || !passEl) return JSON.stringify(state);
              var controls = collectLoginControls();
              var meta = null;
              for (var i=0;i<controls.length;i++){
                if (controls[i].index === index) meta = controls[i];
              }
              var el = window.__tnLoginEls ? window.__tnLoginEls[index] : null;
              if (!meta || !el || !meta.inLoginForm || !safeLoginLabel(meta.text) || forbidden(meta.text)) {
                return JSON.stringify(state);
              }
              setVal(userEl, user);
              setVal(passEl, pass);
              el.click();
              state.loginSubmitted = true;
              return JSON.stringify(state);
            },
            clickLabel: function(label){
              var ok = clickExact(label);
              var state = pageState();
              state.clicked = ok;
              return JSON.stringify(state);
            },
            logoutProbe: function(){
              var state = pageState();
              var loginFields = !!document.querySelector('input[type="password"]');
              var b = bodyText().toLowerCase();
              var auth = false;
              if (!loginFields) {
                if (b.indexOf('deposit address') >= 0 || b.indexOf('log out') >= 0 || b.indexOf('logout') >= 0 || b.indexOf('sign out') >= 0) auth = true;
              }
              state.loginFieldsFound = loginFields;
              state.authenticatedUiVisible = auth;
              state.loggedOut = false;
              return JSON.stringify(state);
            },
            snapshot: function(){
              return JSON.stringify(pageState());
            }
          };
        })();
    """.trimIndent()

    fun install(): String = library

    fun listLoginControls(): String = "window.__tn.listLoginControls()"

    fun fillLogin(username: String, password: String, controlIndex: Int): String =
        "window.__tn.fillLogin(${Safety.jsString(username)}, ${Safety.jsString(password)}, $controlIndex)"

    fun clickLabel(label: String): String {
        require(Safety.allowClick(label)) { "Refusing to click: $label" }
        return "window.__tn.clickLabel(${Safety.jsString(label)})"
    }

    fun logoutProbe(): String = "window.__tn.logoutProbe()"

    fun snapshot(): String = "window.__tn.snapshot()"
}
