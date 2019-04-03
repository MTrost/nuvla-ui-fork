(ns sixsq.nuvla.ui.history.utils
  (:require
    [clojure.string :as str]
    [goog.events :as events]
    [secretary.core :as secretary]
    [taoensso.timbre :as log])
  (:import
    [goog History]
    [goog.history EventType Html5History]
    [goog.history.Html5History TokenTransformer]))


(defn get-token
  "Creates the history token from the given location excluding the query parameters."
  [path-prefix location]
  (let [url (str (.-protocol location) "//" (.-host location) (.-pathname location))]
    (str/replace-first url path-prefix "")))


(defn get-full-token
  "Creates the history token from the given location including the query parameters."
  [path-prefix location]
  (let [url (str (.-protocol location) "//" (.-host location) (.-pathname location) (.-search location))]
    (str/replace-first url path-prefix "")))


(defn create-transformer
  "Saves and restores the URL based on the token provided to the Html5History
   object. The methods of this object are needed when not using fragment based
   routing. The tokens are simply the remaining parts of the URL after the path
   prefix."
  []
  (let [transformer (TokenTransformer.)]
    (set! (.. transformer -retrieveToken)
          (fn [path-prefix location]
            (get-token path-prefix location)))
    (set! (.. transformer -createUrl)
          (fn [token path-prefix location]
            (str path-prefix token)))
    transformer))


(def history
  (doto (Html5History. js/window (create-transformer))
    (events/listen EventType.NAVIGATE (fn [evt] (secretary/dispatch! (.-token evt))))
    (.setUseFragment false)
    (.setEnabled false)))


(defn initialize
  "Sets the path-prefix to use for the history object and enables the object
   to start sending events on token changes."
  [path-prefix]
  (doto history
    (.setPathPrefix path-prefix)
    (.setEnabled true)))


(defn start
  "Sets the starting point for the history. No history event will be generated
   when setting the first value, so this explicitly dispatches the value to the
   URL routing."
  [path-prefix]
  (let [location (.-location js/window)
        token (get-token path-prefix location)
        full-token (get-full-token path-prefix location)]
    (log/info "start token: " token)
    (.setToken history token)
    (secretary/dispatch! full-token)))


(defn navigate
  "Navigates to the given internal URL (relative to the application root) by
   pushing the corresponding token onto the HTML5 history object. Actual
   rerendering will be triggered by the event generated by the history object
   itself."
  [url]
  (log/info "navigating to" url)
  (.setToken history (str "/" url)))


(defn host-url
  "Extracts the host URL from the javascript window.location object."
  []
  (if-let [location (.-location js/window)]
    (let [protocol (.-protocol location)
          host (.-hostname location)
          port (.-port location)
          port-field (when-not (str/blank? port) (str ":" port))]
      (str protocol "//" host port-field))))

