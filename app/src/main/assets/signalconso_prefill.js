// Prefills the SignalConso "Démarchage abusif" wizard from the report plan, and step 4 ("Vos coordonnées")
// from the details the user saved on the phone. It only selects options and fills empty fields, each at
// most once, and never clicks "Suivant", "Continuer" or the final submit: the user reviews every step and
// moves on. The only button it presses is the company search "Rechercher", which looks the company up
// without submitting anything; a SIRET search returning exactly that company gets it selected.
//
// The page's own scripts must see as little of the user's data as possible (security review, SR-01):
// - this file is a function expression the app calls with the plan, which stays in this closure, never
//   in a global; it only works on the report form, over HTTPS;
// - the contact details are not injected with it: the script raises window.__icNeedsContact once step 4
//   shows, and the app then hands them to window.__icFillContact, kept in the closure while step 4 is
//   displayed only (what is typed into the form is the page's anyway);
// - when the user changes their saved details, the app sends null: the script drops what it holds, and the
//   fields still holding what it put there are emptied, so the new details go in;
// - everything is dropped once the report is sent or the page leaves the form.
// The only globals are flags and that entry point: __icPrefillInstalled, __icNeedsContact, __icReportSent,
// __icStep (which step shows, so the app only displays the notes that step needs) and __icPastFirstStep
// (step 2 or later was reached: kept here, since the app only samples the step every 1.5 s).
(function (plan) {
  var FORM_HOST = 'signal.conso.gouv.fr';
  var FORM_PATH = '/fr/demarchage-abusif/faire-un-signalement';
  if (window.__icPrefillInstalled) return;
  window.__icPrefillInstalled = true;
  var done = {};
  var contact = null;
  var observer = null;
  // Fields given a saved value, and that value: kept here rather than on the elements, where the page could
  // still read a value the user erased.
  var contactFilled = new WeakMap();

  function onForm() {
    var path = location.pathname;
    return location.protocol === 'https:' && location.hostname === FORM_HOST &&
      (path === FORM_PATH || path.indexOf(FORM_PATH + '/') === 0);
  }

  // Step 4 is on screen while its first name field is.
  function contactStep() {
    return document.querySelector('input[name="firstName"]');
  }

  // Step 2 asks how to identify the company: "Par son numéro SIRET" / "Par son nom", then its results.
  function companyStep() {
    if (document.getElementById('CompanySearchResult')) return true;
    var labels = document.querySelectorAll('label');
    for (var i = 0; i < labels.length; i++) {
      var text = norm(labels[i].innerText);
      if (text.indexOf('par son numéro siret') === 0 || text.indexOf('par son nom') === 0) return true;
    }
    return false;
  }

  function norm(s) {
    return (s || '').replace(/[’]/g, "'").replace(/\s+/g, ' ').trim().toLowerCase();
  }

  function pickRadio(title) {
    if (!title || done['radio:' + title]) return;
    var labels = document.querySelectorAll('label');
    for (var i = 0; i < labels.length; i++) {
      if (norm(labels[i].innerText).indexOf(norm(title)) !== 0) continue;
      var input = labels[i].htmlFor ? document.getElementById(labels[i].htmlFor) : labels[i].querySelector('input');
      if (input && !input.checked) labels[i].click();
      done['radio:' + title] = true;
      return;
    }
  }

  // React tracks the native value setter: assign through it, then emit input/change so its state follows.
  function setValue(el, value) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function fillOnce(key, el, value) {
    if (!el || !value || done[key]) return;
    done[key] = true;
    setValue(el, value);
  }

  // Step 4 ("Vos coordonnées") also has an input[name=phone], for the user's own number:
  // only target the one labelled as the number that called.
  function callerPhoneInput() {
    var inputs = document.querySelectorAll('input[name="phone"]');
    for (var i = 0; i < inputs.length; i++) {
      var label = inputs[i].id && document.querySelector('label[for="' + inputs[i].id + '"]');
      if (label && norm(label.innerText).indexOf('vous ayant contacté') !== -1) return inputs[i];
    }
    return null;
  }

  // Step 2 company identification: pick "by SIRET" or "by name", fill the field it reveals and run the
  // search once, so the user only has to pick the matching result.
  function searchCompany(radioTitle, fieldName, value) {
    pickRadio(radioTitle);
    if (!done['radio:' + radioTitle] || done['company']) return;
    var input = document.querySelector('input[name="' + fieldName + '"]');
    if (!input) return;
    fillOnce('company', input, value);
    setTimeout(function () {
      for (var node = input.parentElement; node; node = node.parentElement) {
        var buttons = node.querySelectorAll('button');
        for (var i = 0; i < buttons.length; i++) {
          if (norm(buttons[i].innerText) === 'rechercher') { buttons[i].click(); return; }
        }
      }
    }, 300);
  }

  // Step 4 also has input[name=phone], for the user's own number ("Téléphone (facultatif)").
  function contactPhoneInput() {
    var inputs = document.querySelectorAll('input[name="phone"]');
    for (var i = 0; i < inputs.length; i++) {
      var label = inputs[i].id && document.querySelector('label[for="' + inputs[i].id + '"]');
      if (label && norm(label.innerText).indexOf('vous ayant contacté') === -1) return inputs[i];
    }
    return null;
  }

  function contactFields() {
    return {
      firstName: document.querySelector('input[name="firstName"]'),
      lastName: document.querySelector('input[name="lastName"]'),
      email: document.querySelector('input[name="email"]'),
      phone: contactPhoneInput(),
      referenceNumber: document.querySelector('input[name="referenceNumber"]')
    };
  }

  // Saved contact details go into empty fields only, and never twice into the same field: a value the
  // user erased stays erased. SignalConso rebuilds step 4 empty after "Précédent", which gets refilled.
  function fillContact() {
    var fields = contactFields();
    for (var key in fields) {
      var el = fields[key], value = contact[key];
      if (!el || !value || el.value || contactFilled.get(el) === value) continue;
      contactFilled.set(el, value);
      setValue(el, value);
    }
    if (contact.shareContact === true || contact.shareContact === false) {
      pickShareChoice(contact.shareContact ? 'Je partage mes coordonnées' : 'Je ne partage pas mes coordonnées');
    }
  }

  // The fields still holding the details given before are emptied; a value the user typed or changed stays.
  // The share choice stays as it is: a radio can't be unset, and the user sees it.
  function dropContact() {
    var fields = contactFields();
    for (var key in fields) {
      var el = fields[key];
      if (!el || !contactFilled.has(el) || el.value !== contactFilled.get(el)) continue;
      contactFilled.delete(el);
      setValue(el, '');
    }
  }

  // "Souhaitez-vous partager vos coordonnées avec l'entreprise ?": answered only while neither option is
  // checked, so a choice the user makes is kept (and a step 4 rebuilt empty gets it again).
  function pickShareChoice(title) {
    var labels = document.querySelectorAll('label');
    for (var i = 0; i < labels.length; i++) {
      if (norm(labels[i].innerText).indexOf(norm(title)) !== 0) continue;
      var input = labels[i].htmlFor ? document.getElementById(labels[i].htmlFor) : labels[i].querySelector('input');
      if (!input || contactFilled.has(input)) return;
      var group = input.name ? document.querySelectorAll('input[type="radio"][name="' + input.name + '"]') : [input];
      for (var j = 0; j < group.length; j++) if (group[j].checked) return;
      contactFilled.set(input, true);
      labels[i].click();
      return;
    }
  }

  // "C'est une entreprise étrangère" opens a form of its own: company name, country, postal code. The name
  // is the plan's; the country and the postal code are left to the user (SignalConso wants a French postal
  // code even there, and the company's country can't be told reliably from the plan).
  var foreignNameFilled = new WeakSet();

  function foreignCompanyChosen() {
    var labels = document.querySelectorAll('label');
    for (var i = 0; i < labels.length; i++) {
      if (norm(labels[i].innerText).indexOf("c'est une entreprise étrangère") !== 0) continue;
      var input = labels[i].htmlFor ? document.getElementById(labels[i].htmlFor) : labels[i].querySelector('input');
      return !!(input && input.checked);
    }
    return false;
  }

  function fillForeignCompanyName(name) {
    if (!name || !foreignCompanyChosen()) return;
    var input = document.querySelector('input[name="name"]');
    if (!input || input.value || foreignNameFilled.has(input)) return;
    foreignNameFilled.add(input);
    setValue(input, name);
  }

  // A SIRET/SIREN search returns the matching establishment(s): select it only when there is a single
  // one carrying that number. A name search is left to the user, its results are too loose.
  function selectSiretResult(siret) {
    if (done['companyResult']) return;
    var radios = document.querySelectorAll('#CompanySearchResult input[type="radio"]');
    if (!radios.length) return;
    done['companyResult'] = true;
    if (radios.length !== 1) return;
    var label = radios[0].id && document.querySelector('label[for="' + radios[0].id + '"]');
    var digits = label ? label.innerText.replace(/\s+/g, '') : '';
    if (digits.indexOf('SIRET' + siret) !== -1 && !radios[0].checked) label.click();
  }

  // SignalConso's acknowledgment once a report is sent. The app polls window.__icReportSent to count it.
  var SENT = ['votre signalement a été envoyé', 'votre signalement ne sera pas transmis à cette entreprise'];

  function detectReportSent() {
    // The page content only: body.textContent would include Next.js's inline script payloads.
    var main = document.querySelector('main');
    if (window.__icReportSent || !main) return;
    var text = norm(main.textContent);
    for (var i = 0; i < SENT.length; i++) {
      if (text.indexOf(SENT[i]) !== -1) { window.__icReportSent = true; return; }
    }
  }

  // Nothing of the user's is kept once it can no longer be needed.
  function forget() {
    plan = null;
    contact = null;
    window.__icNeedsContact = false;
    window.__icStep = 'other';
  }

  function run() {
    detectReportSent();
    if (window.__icReportSent) {
      forget();
      delete window.__icFillContact;
      if (observer) observer.disconnect();
      return;
    }
    if (!onForm()) { forget(); return; }
    window.__icStep = contactStep() ? 'contact' : companyStep() ? 'company' : 'other';
    // Step 3 shows the field for the number that called: reaching it also means step 1 is behind.
    if (window.__icStep !== 'other' || callerPhoneInput()) window.__icPastFirstStep = true;
    // The contact details only live here while step 4 is displayed; a step 4 shown again asks for them again.
    if (!contactStep()) contact = null;
    window.__icNeedsContact = !!contactStep() && !contact;
    if (contact) fillContact();
    if (!plan) return;
    pickRadio(plan.problem);
    pickRadio(plan.subcategory);
    fillOnce('phone', callerPhoneInput(), plan.phone);
    if (plan.company && plan.company.siret) {
      searchCompany('Par son numéro SIRET', 'identity', plan.company.siret);
      selectSiretResult(plan.company.siret);
    }
    else if (plan.company) searchCompany('Par son nom', 'name', plan.company.name);
    if (plan.company) fillForeignCompanyName(plan.company.name);
    var dates = document.querySelectorAll('input[type="date"]');
    for (var i = 0; i < dates.length && plan.dates && i < plan.dates.length; i++) {
      fillOnce('date:' + i, dates[i], plan.dates[i]);
    }
    fillOnce('description', document.querySelector('textarea'), plan.description);
  }

  // The app's way in for the contact details, at step 4 only (null drops them, after a profile change, with
  // what the script put in the fields shown).
  window.__icFillContact = function (details) {
    if (!details && contactStep() && onForm()) dropContact();
    contact = details && contactStep() && onForm() ? details : null;
    run();
  };

  observer = new MutationObserver(run);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  run();
})
