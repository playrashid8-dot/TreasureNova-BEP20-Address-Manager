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
            if (b.indexOf('verification amount') >= 0 && b.indexOf('authenticator') < 0 && b.indexOf('2fa') < 0) {
              /* payment-amount copy is not 2FA */
            }
            return b.indexOf('authenticator') >= 0 || b.indexOf('two-factor') >= 0 || b.indexOf('2fa') >= 0 || b.indexOf('enter the 6') >= 0 || b.indexOf('google verification code') >= 0;
          }
          function loginError(){
            var b = bodyText().toLowerCase();
            var marks = ['incorrect password','invalid password','wrong password','login failed','account or password'];
            for (var i=0;i<marks.length;i++){ if (b.indexOf(marks[i]) >= 0) return marks[i]; }
            return '';
          }
          function readBep20(){
            var nodes = document.querySelectorAll('div,span,p,label,h1,h2,h3');
            for (var i=0;i<nodes.length;i++){
              var label = text(nodes[i]);
              if (!/^USDT Deposit Address\s*\(BEP-?20\)$/i.test(label) && label.toLowerCase() !== 'bep20' && label.toLowerCase() !== 'bep-20' && label.toLowerCase() !== 'bnb smart chain') continue;
              if (/TRC/i.test(label)) continue;
              var node = nodes[i].parentElement;
              for (var depth=0; depth<5 && node; depth++){
                var block = text(node);
                if (/TRC-?20/i.test(block.split('USDT Deposit Address')[0] || '') && !/BEP-?20/i.test(label)) break;
                var match = block.match(/0x[a-fA-F0-9]{40}/);
                var own = text(nodes[i]);
                if (match && /BEP-?20|BNB Smart Chain|BSC/i.test(block)) {
                  var after = block.split(/USDT Deposit Address\s*\(BEP-?20\)/i)[1] || block;
                  var precise = after.match(/0x[a-fA-F0-9]{40}/);
                  if (/TRC-?20/i.test(after.slice(0, precise ? after.indexOf(precise[0]) : 0))) { node = node.parentElement; continue; }
                  return {present:true, network: own || 'BEP-20', value: (precise||match)[0]};
                }
                node = node.parentElement;
              }
            }
            var titles = document.querySelectorAll('.recharge-required-modal__address-title');
            for (var j=0;j<titles.length;j++){
              if (!/BEP-?20/i.test(text(titles[j]))) continue;
              var row = titles[j].parentElement && titles[j].parentElement.parentElement;
              var valueEl = row ? row.querySelector('.recharge-required-modal__address-value') : null;
              return {present:true, network:'BEP-20', value: text(valueEl)};
            }
            return {present:false, network:'', value:''};
          }
          window.__tn = {
            allow: function(label){ return !forbidden(label); },
            fillLogin: function(user, pass){
              var userEl = document.querySelector('input[placeholder="Username/Email"]') || document.querySelector('input[placeholder*="Username" i]');
              var passEl = document.querySelector('input[placeholder="Password"]') || document.querySelector('input[type="password"]');
              if (!userEl || !passEl) return JSON.stringify({loginFieldsFound:false, loginSubmitted:false, blockedMessage:blocked(), twoFactorVisible:twoFactor(), loginError:loginError()});
              setVal(userEl, user);
              setVal(passEl, pass);
              var root = passEl.closest('form');
              var scope = root ? (root.parentElement || root) : document;
              var buttons = scope.querySelectorAll('button');
              var submitted = false;
              for (var i=0;i<buttons.length;i++){
                var t = text(buttons[i]);
                if (t === 'Confirm' && !forbidden(t)) { buttons[i].click(); submitted = true; break; }
              }
              return JSON.stringify({loginFieldsFound:true, loginSubmitted:submitted, blockedMessage:blocked(), twoFactorVisible:twoFactor(), loginError:loginError()});
            },
            clickLabel: function(label){
              var ok = clickExact(label);
              var bep = readBep20();
              return JSON.stringify({clicked:ok, blockedMessage:blocked(), twoFactorVisible:twoFactor(), loginError:loginError(), bep20LabelPresent:bep.present, networkText:bep.network, bep20Value:bep.value});
            },
            snapshot: function(){
              var bep = readBep20();
              return JSON.stringify({loginFieldsFound:!!document.querySelector('input[type="password"]'), blockedMessage:blocked(), twoFactorVisible:twoFactor(), loginError:loginError(), bep20LabelPresent:bep.present, networkText:bep.network, bep20Value:bep.value});
            }
          };
        })();
    """.trimIndent()

    fun install(): String = library

    fun fillLogin(username: String, password: String): String =
        "window.__tn.fillLogin(${Safety.jsString(username)}, ${Safety.jsString(password)})"

    fun clickLabel(label: String): String {
        require(Safety.allowClick(label)) { "Refusing to click: $label" }
        return "window.__tn.clickLabel(${Safety.jsString(label)})"
    }

    fun snapshot(): String = "window.__tn.snapshot()"
}
