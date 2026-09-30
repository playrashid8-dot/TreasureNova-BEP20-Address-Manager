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
            var nodes = document.querySelectorAll('button, a, input[type="submit"], input[type="button"]');
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
          function usernameWithin(root){
            if (!root || !root.querySelector) return null;
            var el = root.querySelector('input[placeholder="Username/Email"]')
              || root.querySelector('input[placeholder*="Username" i]')
              || root.querySelector('input[type="email"]')
              || root.querySelector('input[name="username"]')
              || root.querySelector('input[name="email"]');
            if (!el) {
              var texts = root.querySelectorAll('input[type="text"], input:not([type])');
              for (var i=0;i<texts.length;i++){
                var kind = (texts[i].getAttribute('type') || 'text').toLowerCase();
                if (kind === 'password' || kind === 'hidden' || kind === 'submit' || kind === 'button') continue;
                el = texts[i];
                break;
              }
            }
            if (el && root.contains && root.contains(el)) return el;
            return null;
          }
          function loginRoot(){
            var passwords = document.querySelectorAll('input[type="password"]');
            for (var i=0;i<passwords.length;i++){
              var pass = passwords[i];
              var form = pass.closest ? pass.closest('form') : null;
              var root = form;
              if (!root) {
                var node = pass.parentElement;
                if (node && node.parentElement && node.parentElement.tagName !== 'BODY' && node.parentElement.tagName !== 'HTML') root = node.parentElement;
                else root = node;
              }
              if (!root || !root.contains || !root.contains(pass)) continue;
              var userEl = usernameWithin(root);
              if (!userEl || !root.contains(userEl)) continue;
              return root;
            }
            return null;
          }
          function collectLoginControls(){
            var root = loginRoot();
            var action = '';
            if (root && root.tagName === 'FORM') action = root.getAttribute('action') || '';
            var nodes = root && root.querySelectorAll ? root.querySelectorAll('button, input[type="submit"], input[type="button"]') : [];
            var els = [];
            var out = [];
            for (var i=0;i<nodes.length;i++){
              var el = nodes[i];
              if (!root.contains(el)) continue;
              var tag = el.tagName.toLowerCase();
              var type = (el.getAttribute('type') || '').toLowerCase();
              var label = tag === 'input' ? (el.value || '') : text(el);
              label = (label || '').replace(/\s+/g,' ').trim();
              if (!label || label.length > 48) continue;
              var submit = type === 'submit' || (tag === 'button' && (type === '' || type === 'submit'));
              els.push(el);
              out.push({index: out.length, text: label, tag: tag, type: type, inLoginForm: true, submitControl: submit, formAction: action});
            }
            window.__tnLoginEls = els;
            return out;
          }
          function signalCount(value){
            var n = 0;
            if (/\busdt\b/i.test(value)) n++;
            if (/(^|[^A-Za-z0-9])bep-?20([^A-Za-z0-9]|$)|bnb smart chain/i.test(value)) n++;
            if (/0x[a-fA-F0-9]{40}/i.test(value)) n++;
            return n;
          }
          function isUnrelatedParent(el){
            var kids = el.children || [];
            var parts = [];
            for (var i=0;i<kids.length;i++){
              var ct = text(kids[i]);
              if (ct) parts.push(ct);
            }
            if (parts.length < 2) return false;
            var joined = parts.join(' ').replace(/\s+/g,' ').trim();
            if (joined !== text(el)) return false;
            var seen = {};
            var addrCount = 0;
            var rejected = false;
            var bep = false;
            for (var p=0;p<parts.length;p++){
              var found = parts[p].match(/0x[a-fA-F0-9]{40}/g) || [];
              for (var j=0;j<found.length;j++){
                var key = found[j].toLowerCase();
                if (!seen[key]) { seen[key] = 1; addrCount++; }
              }
              if (/trc-?20|\btron\b|erc-?20/i.test(parts[p])) rejected = true;
              if (/(^|[^A-Za-z0-9])bep-?20([^A-Za-z0-9]|$)|bnb smart chain/i.test(parts[p])) bep = true;
            }
            if (addrCount > 1) return true;
            if (rejected && bep) return true;
            var paired = false;
            var hasUsdt = false;
            var hasBep = false;
            var hasAddr = false;
            var labelChild = false;
            for (var k=0;k<parts.length;k++){
              var part = parts[k];
              var hu = /\busdt\b/i.test(part);
              var hb = /(^|[^A-Za-z0-9])bep-?20([^A-Za-z0-9]|$)|bnb smart chain/i.test(part);
              var ha = /0x[a-fA-F0-9]{40}/i.test(part);
              if (hu) hasUsdt = true;
              if (hb) hasBep = true;
              if (ha) hasAddr = true;
              if (ha && (hu || hb)) paired = true;
              if (hu && hb && !ha) labelChild = true;
            }
            if (labelChild && hasAddr && !paired && addrCount === 1 && !rejected) return false;
            if (hasUsdt && hasBep && hasAddr && !paired) return true;
            return false;
          }
          function depositBlocks(){
            var nodes = document.querySelectorAll('section, article, li, tr, td, div, p, span');
            var out = [];
            var seen = {};
            for (var i=0;i<nodes.length;i++){
              var el = nodes[i];
              if (isUnrelatedParent(el)) continue;
              var block = text(el);
              if (!block || block.length > 800) continue;
              if (signalCount(block) === 0 && !/trc|tron|bnb/i.test(block)) continue;
              if (seen[block]) continue;
              seen[block] = 1;
              out.push(block);
              if (out.length >= 40) break;
            }
            return out;
          }
          function authenticatedUi(){
            var b = bodyText().toLowerCase();
            var marks = ['log out','logout','sign out','deposit address','my wallet'];
            for (var i=0;i<marks.length;i++){ if (b.indexOf(marks[i]) >= 0) return true; }
            return false;
          }
          function loggedOutUi(){
            if (authenticatedUi()) return false;
            var b = bodyText().toLowerCase();
            var marks = ['sign in','log in','login'];
            for (var i=0;i<marks.length;i++){ if (b.indexOf(marks[i]) >= 0) return true; }
            return false;
          }
          function accountIdentity(){
            var selectors = ['[data-username]','[data-account]','.username','.user-name','.account-name','.nickname'];
            for (var s=0;s<selectors.length;s++){
              var nodes = document.querySelectorAll(selectors[s]);
              for (var i=0;i<nodes.length;i++){
                if (nodes[i].querySelector && nodes[i].querySelector('input, textarea, select')) continue;
                var t = text(nodes[i]);
                if (t && t.length <= 80) return t;
              }
            }
            return '';
          }
          function pageState(){
            return {
              loginFieldsFound: !!loginRoot(),
              blockedMessage: blocked(),
              twoFactorVisible: twoFactor(),
              loginError: loginError(),
              depositBlocks: depositBlocks(),
              loggedOut: false,
              authenticatedUiVisible: authenticatedUi(),
              loggedOutUiVisible: loggedOutUi(),
              accountIdentity: accountIdentity()
            };
          }
          window.__tn = {
            allow: function(label){ return !forbidden(label); },
            listLoginControls: function(){
              return JSON.stringify(collectLoginControls());
            },
            fillLogin: function(user, pass, index){
              var state = pageState();
              var root = loginRoot();
              var userEl = root ? usernameWithin(root) : null;
              var passEl = root ? root.querySelector('input[type="password"]') : null;
              state.loginFieldsFound = !!(root && userEl && passEl && root.contains(userEl) && root.contains(passEl));
              state.loginSubmitted = false;
              if (!state.loginFieldsFound) return JSON.stringify(state);
              var controls = collectLoginControls();
              var meta = null;
              for (var i=0;i<controls.length;i++){
                if (controls[i].index === index) meta = controls[i];
              }
              var el = window.__tnLoginEls ? window.__tnLoginEls[index] : null;
              if (!meta || !el || !root.contains(el) || !meta.inLoginForm || !safeLoginLabel(meta.text) || forbidden(meta.text)) {
                return JSON.stringify(state);
              }
              if (userEl.form && passEl.form && userEl.form !== passEl.form) return JSON.stringify(state);
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
              state.loginFieldsFound = !!document.querySelector('input[type="password"]');
              state.authenticatedUiVisible = authenticatedUi();
              state.loggedOutUiVisible = loggedOutUi();
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
