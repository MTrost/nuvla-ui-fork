(ns sixsq.nuvla.ui.apps.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.ui.utils.semantic-ui :as ui]
    [taoensso.timbre :as log]))


(defn nav-path->module-path
  [nav-path]
  (some->> nav-path rest seq (str/join "/")))


(defn nav-path->parent-path
  [nav-path]
  (some->> nav-path rest drop-last seq (str/join "/")))


(defn nav-path->module-name
  [nav-path]
  (some->> nav-path rest last))


(defn category-icon
  [category]
  (case category
    "PROJECT" "folder"
    "APPLICATION" "sitemap"
    "IMAGE" "file"
    "COMPONENT" "microchip"
    "question circle"))


(defn meta-category-icon
  [category]
  (if (= "PROJECT" category)
    "folder open"
    (category-icon category)))


;; Sanitize before serialization to server

(defn sanitize-name [name]
  (str/lower-case
    (str/replace
      (str/trim
        (str/join "" (re-seq #"[a-zA-Z0-9\ ]" name)))
      " " "-")))


(defn contruct-path [parent name]
  (let [sanitized-name (sanitize-name name)]
    (str/join "/"
              (remove str/blank?
                      [parent sanitized-name]))))


(defn sanitize-base
  [module]
  (let [path (contruct-path (:parent-path module) (:name module))]
    (if (nil? (:path module))
      (assoc module :path path)
      module)))


(defn process-urls
  [db]
  (for [u [(:sixsq.nuvla.ui.apps-component.spec/urls db)]]
    (conj [(:name u) (:url u)])))


(defn sanitize-module-component
  [module commit-map db]
  (let [{:keys [author commit]} commit-map
        urls (process-urls db)]
    (log/infof "urls: %s" (get-in db :sixsq.nuvla.ui.apps-component.spec/urls))
    (log/infof "urls after: %s" urls)
    (-> module
        (sanitize-base)
        (assoc-in [:content :author] author)
        (assoc-in [:content :commit] commit)
        (assoc-in [:content :architecture] (::architecture module))
        (assoc-in [:content :urls] (urls db))
        )))


(defn sanitize-module-project
  [module]
  (-> module
      (sanitize-base)
      (dissoc :children)))


(defn sanitize-module
  [module commit db]
  (let [type (:type module)]
    (cond
      (= "COMPONENT" type) (sanitize-module-component module commit db)
      (= "PROJECT" type) (sanitize-module-project module)
      :else module)))


(defn can-operation? [operation data]
  (->> data :operations (map :rel) (some #{(name operation)}) nil? not))


(defn can-edit? [data]
  (can-operation? :edit data))


(defn editable?
  [module is-new?]
  (or is-new? (can-edit? module)))


(defn mandatory-name
  [name]
  [:span name [:sup " " [ui/Icon {:name  :asterisk
                                  :size  :tiny
                                  :color :red}]]])
