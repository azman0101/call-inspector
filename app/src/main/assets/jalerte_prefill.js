// Prefills J'alerte l'Arcep (jalerte.arcep.fr/jalerte/) for telemarketing calls. It only picks options and
// fills empty fields, each at most once, and never clicks "Poursuivre" or "Envoyer mon alerte": the user
// reviews every step and sends the alert. An option or field the user already set is left alone. Where it
// answers for the user (5G, not in transport, customer service not contacted, no transmission to third
// parties), it gives the answer they asked for, and they can change it; the consent to the Arcep's
// processing stays theirs to tick.
//
// The site is a Wicket application: every choice is sent to the server, which redraws the form. The script
// makes one such change per pass, then runs again on the redraw (MutationObserver) or, for a change the
// server does not redraw anything for, shortly after.
//
// The page's own scripts see as little of the user's data as possible (as for SignalConso, SR-01):
// - this file is a function expression the app calls with the plan, which stays in this closure;
// - the user's commune (postal code, city) and contact details are not injected with it: the script raises
//   window.__iaNeedsCommune at step 3 and window.__iaNeedsContact at step 5, and the app then hands them to
//   window.__iaFillCommune / window.__iaFillContact, kept in the closure while that step is shown only;
// - when the user changes their saved details, the app sends null: the script drops what it holds, and the
//   commune or contact fields still holding what it put there are emptied, so the new details go in;
// - it only works on the alert form, over HTTPS.
// Other globals are flags: __iaPrefillInstalled, and __iaStep (the step shown, 1 to 5, 0 elsewhere).
// Once J'alerte l'Arcep confirms the alert ("Votre alerte a été soumise"), everything is dropped and the
// script stops; the app counts the alert from that message itself.
(function (plan) {
  var FORM_HOST = 'jalerte.arcep.fr';
  if (window.__iaPrefillInstalled) return;
  window.__iaPrefillInstalled = true;
  var done = {};
  var contact = null;
  var where = null;
  // Fields given a value from the user's details, and that value: kept here, not on the elements.
  var filled = new WeakMap();
  var pending = null;
  var NEXT_PASS_MS = 400;
  // The commune the script selected (its value), and one the profile change made stale, to be dropped at step 3.
  var picked = null;
  var stalePick = null;

  function onForm() {
    var path = location.pathname;
    return location.protocol === 'https:' && location.hostname === FORM_HOST && (path === '/jalerte/' || path === '/jalerte');
  }

  function norm(s) {
    return (s || '').replace(/[’]/g, "'").replace(/\s+/g, ' ').trim().toLowerCase();
  }

  function labelText(input) {
    var label = input.id && document.querySelector('label[for="' + input.id + '"]');
    return label ? norm(label.innerText || label.textContent) : '';
  }

  // Radios and checkboxes are named by group; their labels carry the wording.
  function group(name) {
    return document.querySelectorAll('input[name="' + name + '"]');
  }

  function anyChecked(inputs) {
    for (var i = 0; i < inputs.length; i++) if (inputs[i].checked) return true;
    return false;
  }

  // The input of [inputs] labelled [text], or labelled [text] followed by a precision in brackets.
  function labelled(inputs, text) {
    var want = norm(text);
    for (var i = 0; i < inputs.length; i++) {
      var t = labelText(inputs[i]);
      if (t === want || t.indexOf(want + ' (') === 0) return inputs[i];
    }
    return null;
  }

  // Selects and text fields send their value to the server on "change".
  function changed(el) {
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function setValue(el, value) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
    changed(el);
  }

  function field(selector) {
    return document.querySelector(selector);
  }

  // Each step is recognized by a field only it has.
  function currentStep() {
    if (field('input[name="utilisateurContainer:email"]')) return 5;
    if (field('textarea[name="verbatim"]')) return 4;
    if (field('select[name="operateurPrincipalContainer:operateurPrincipal"]')) return 3;
    if (field('input[name="problematique"]')) return 2;
    if (field('input[name="profil"]')) return 1;
    return 0;
  }

  // Clicks [text] in the radio group [name] once, unless something is already checked there.
  function pickOnce(name, text) {
    if (done['radio:' + name]) return false;
    var inputs = group(name);
    if (!inputs.length) return false;
    done['radio:' + name] = true;
    if (anyChecked(inputs)) return false;
    var input = labelled(inputs, text);
    if (!input) return false;
    input.click();
    return true;
  }

  function chooseOnce(name, value) {
    if (done['select:' + name]) return false;
    var select = field('select[name="' + name + '"]');
    if (!select) return false;
    done['select:' + name] = true;
    if (select.value) return false;
    for (var i = 0; i < select.options.length; i++) {
      if (select.options[i].value === value) {
        select.value = value;
        changed(select);
        return true;
      }
    }
    return false;
  }

  // Step 1, "Contexte": a private person, about fraud and telemarketing, on a mobile phone.
  function step1() {
    return pickOnce('profil', 'Particulier') ||
      pickOnce('marche', 'Fraudes et démarchage') ||
      chooseOnce('marcheContainer:fraudesEtDemarchageType', 'MOBILE');
  }

  // Step 2, "Diagnostic": unsolicited telemarketing, and the kind of number(s) that called.
  function step2() {
    if (pickOnce('problematique', 'Démarchage commercial non sollicité')) return true;
    var sub = group('sousProblematique');
    if (!done.sub && sub.length === 1) {
      done.sub = true;
      if (!sub[0].checked) { sub[0].click(); return true; }
    }
    var boxes = document.querySelectorAll('input[type="checkbox"][name*="choixMultipleContainer"]');
    if (!boxes.length || done.types) return false;
    if (done.typesStarted === undefined) done.typesStarted = !anyChecked(boxes);
    if (!done.typesStarted) { done.types = true; return false; }
    var types = plan.numberTypes || [];
    for (var i = 0; i < types.length; i++) {
      if (done['type:' + types[i]]) continue;
      done['type:' + types[i]] = true;
      var box = labelled(boxes, types[i]);
      if (box && !box.checked) { box.click(); return true; }
    }
    done.types = true;
    return false;
  }

  // Step 3, "Détails": 5G, the operator the ARCEP assigned the number to (from J'alerte l'Arcep's list or in
  // "Autre"), not in transport, and the commune.
  function step3() {
    if (chooseOnce('technologieContainer:technologie', 'CINQ_G')) return true;
    var select = field('select[name="operateurPrincipalContainer:operateurPrincipal"]');
    var other = field('input[name="operateurPrincipalAutreContainer:operateurPrincipalAutre"]');
    if (!done.operator && select) {
      done.operator = true;
      if (!select.value && !(other && other.value)) {
        if (plan.jalerteOperator) {
          for (var i = 0; i < select.options.length; i++) {
            if (select.options[i].textContent.trim() === plan.jalerteOperator) {
              select.value = select.options[i].value;
              changed(select);
              return true;
            }
          }
        }
        if (other && plan.operatorName) { setValue(other, plan.operatorName); return true; }
      }
    }
    if (pickOnce('localisationContainer:transports', 'Non')) return true;
    if (where) fillCommune();
    return false;
  }

  function communeSelect() {
    return field('select[name="localisationContainer:commune"]');
  }

  // The commune list is searched on the server (select2): by postal code, then the city picks among the
  // communes sharing it. Without a single answer, the search is left open with the postal code typed.
  function fillCommune() {
    var select = communeSelect();
    if (!select || select.value || done.commune) return;
    done.commune = true;
    var url = searchUrl(select);
    if (!url || !where.postalCode) { openSearch(select); return; }
    var params = new URLSearchParams({
      q: where.postalCode, page: '1', 'wicket-ajax': 'true',
      'wicket-ajax-baseurl': location.protocol + '//' + location.host + location.pathname
    });
    fetch(url + (url.indexOf('?') < 0 ? '?' : '&') + params.toString(), { credentials: 'same-origin' })
      .then(function (response) { return response.json(); })
      .then(function (result) {
        var item = pickCommune((result && result.items) || [], where && where.city);
        var current = communeSelect();
        if (!current || current.value) return;
        if (!item) { openSearch(current); return; }
        var option = document.createElement('option');
        option.value = item.id;
        option.textContent = item.text;
        current.appendChild(option);
        current.value = item.id;
        picked = current.value;
        changed(current);
      })
      .catch(function () { openSearch(communeSelect()); });
  }

  function searchUrl(select) {
    var $ = window.jQuery;
    var select2 = $ && $(select).data('select2');
    var ajax = select2 && select2.options && select2.options.get && select2.options.get('ajax');
    return ajax && ajax.url ? new URL(ajax.url, location.href).href : null;
  }

  // "Paris 11e Arrondissement (75011)": the city is the text before the postal code.
  function pickCommune(items, city) {
    if (items.length === 1) return items[0];
    var c = norm(city);
    if (!c) return null;
    var exact = items.filter(function (it) { return norm(it.text).replace(/\s*\(\d+\)$/, '') === c; });
    if (exact.length === 1) return exact[0];
    var starts = items.filter(function (it) { return norm(it.text).indexOf(c) === 0; });
    return starts.length === 1 ? starts[0] : null;
  }

  // The commune selected from the old postal code goes, unless the user chose another one since.
  function dropStaleCommune() {
    var select = communeSelect();
    if (!select) return;
    if (select.value === stalePick) {
      for (var i = select.options.length - 1; i >= 0; i--) {
        if (select.options[i].value === stalePick) select.remove(i);
      }
      select.value = '';
      changed(select);
    }
    stalePick = null;
  }

  function openSearch(select) {
    var $ = window.jQuery;
    if (!select || !$ || !$(select).data('select2') || !where || !where.postalCode) return;
    $(select).select2('open');
    var input = document.querySelector('.select2-search__field');
    if (input) setValue(input, where.postalCode);
  }

  // Step 4, "Compléments": what happened, with the numbers and dates from the call log.
  function step4() {
    var text = field('textarea[name="verbatim"]');
    if (done.verbatim || !text) return false;
    done.verbatim = true;
    if (text.value || !plan.description) return false;
    setValue(text, plan.description);
    return true;
  }

  function contactFields() {
    return {
      email: field('input[name="utilisateurContainer:email"]'),
      lastName: field('input[name="utilisateurContainer:nom"]'),
      firstName: field('input[name="utilisateurContainer:prenom"]'),
      phone: field('input[name="utilisateurContainer:telephone"]')
    };
  }

  // Step 5, "Validation": the user's contact details, into empty fields only, each value once per field.
  function fillContact() {
    var fields = contactFields();
    for (var key in fields) {
      var el = fields[key], value = contact[key];
      if (!el || !value || el.value || filled.get(el) === value) continue;
      filled.set(el, value);
      setValue(el, value);
    }
  }

  // The fields still holding the details given before are emptied; a value the user typed or changed stays.
  function dropContact() {
    var fields = contactFields();
    for (var key in fields) {
      var el = fields[key];
      if (!el || !filled.has(el) || el.value !== filled.get(el)) continue;
      filled.delete(el);
      setValue(el, '');
    }
  }

  // Step 5, "Validation": customer service not contacted, alert not passed on to third parties. The consent
  // to the Arcep's processing, the rating and the sending are the user's.
  function step5() {
    return pickOnce('serviceClientContainer:contacte', 'Non') || pickOnce('consentementTiers', 'Non');
  }

  function forget() {
    plan = null;
    contact = null;
    where = null;
    window.__iaNeedsContact = false;
    window.__iaNeedsCommune = false;
    window.__iaStep = 0;
  }

  function sent() {
    var b = document.body;
    var text = b ? norm(b.innerText || b.textContent) : '';
    return text.indexOf('votre alerte a été soumise') !== -1;
  }

  function run() {
    if (!onForm()) { forget(); return; }
    if (sent()) {
      forget();
      delete window.__iaFillContact;
      delete window.__iaFillCommune;
      if (observer) observer.disconnect();
      return;
    }
    var step = currentStep();
    window.__iaStep = step;
    // The user's details only live here while the step that needs them is shown.
    if (step !== 5) contact = null;
    if (step !== 3) where = null;
    if (step === 3 && stalePick !== null) dropStaleCommune();
    var commune = communeSelect();
    window.__iaNeedsCommune = step === 3 && !!commune && !commune.value && !where && !done.commune;
    window.__iaNeedsContact = step === 5 && !contact;
    if (step === 5 && contact) fillContact();
    if (!plan) return;
    var acted = step === 1 ? step1() : step === 2 ? step2() : step === 3 ? step3() : step === 4 ? step4() : step === 5 ? step5() : false;
    if (acted && !pending) pending = setTimeout(function () { pending = null; run(); }, NEXT_PASS_MS);
  }

  // The app's ways in for the user's details (null drops them).
  window.__iaFillCommune = function (details) {
    // Dropped (the profile changed): a new postal code gets a new search, even after a failed one, and
    // replaces the commune found from the old one.
    if (!details) {
      done.commune = false;
      if (picked !== null) { stalePick = picked; picked = null; }
    }
    where = details && currentStep() === 3 && onForm() ? details : null;
    run();
  };
  window.__iaFillContact = function (details) {
    // Dropped (the profile changed): what the script put in the fields shown goes too.
    if (!details && currentStep() === 5 && onForm() && !sent()) dropContact();
    contact = details && currentStep() === 5 && onForm() ? details : null;
    run();
  };

  var observer = new MutationObserver(run);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  run();
})
