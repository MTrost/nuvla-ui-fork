(ns sixsq.nuvla.ui.i18n.spec
  (:require
    [clojure.spec.alpha :as s]))

(s/def ::locale string?)

(s/def ::tr fn?)

(s/def ::db (s/keys :req [::locale ::tr]))

(def defaults {::locale "en"})
