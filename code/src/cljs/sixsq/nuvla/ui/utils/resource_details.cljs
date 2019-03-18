(ns sixsq.nuvla.ui.utils.resource-details
  (:require
    [cljs.pprint :refer [pprint]]
    [re-frame.core :refer [dispatch subscribe]]
    [reagent.core :as r]
    [sixsq.nuvla.ui.cimi-api.utils :as cimi-api-utils]
    [sixsq.nuvla.ui.cimi-detail.events :as api-detail-events]
    [sixsq.nuvla.ui.cimi.subs :as cimi-subs]
    [sixsq.nuvla.ui.docs.subs :as docs-subs]
    [sixsq.nuvla.ui.i18n.subs :as i18n-subs]
    [sixsq.nuvla.ui.utils.collapsible-card :as cc]
    [sixsq.nuvla.ui.utils.form-fields :as ff]
    [sixsq.nuvla.ui.utils.forms :as forms]
    [sixsq.nuvla.ui.utils.general :as general]
    [sixsq.nuvla.ui.utils.semantic-ui :as ui]
    [sixsq.nuvla.ui.utils.semantic-ui-extensions :as uix]
    [sixsq.nuvla.ui.utils.style :as style]
    [sixsq.nuvla.ui.utils.table :as table]
    [sixsq.nuvla.ui.utils.time :as time]
    [sixsq.nuvla.ui.utils.values :as values]))


(defn action-buttons
  [confirm-label cancel-label on-confirm on-cancel]
  [:div
   [uix/Button
    {:text     cancel-label
     :on-click on-cancel}]
   [uix/Button
    {:text     confirm-label
     :primary  true
     :on-click on-confirm}]])


(defn action-button-icon
  [label icon title-text body on-confirm on-cancel & [scrolling? position]]
  (let [show? (r/atom false)]
    (fn [label icon title-text body on-confirm on-cancel & [scrolling? position]]
      (let [action-fn (fn []
                        (reset! show? false)
                        (on-confirm))
            cancel-fn (fn []
                        (reset! show? false)
                        (on-cancel))]

        [ui/Modal
         {:open      (boolean @show?)
          :closeIcon true
          :on-close  #(reset! show? false)
          :trigger   (r/as-element
                       [ui/MenuItem (cond-> {:aria-label label, :name label, :on-click #(reset! show? true)}
                                            position (assoc :position position))
                        (when icon
                          [ui/Icon {:name icon}])
                        label])}
         [ui/ModalHeader title-text]
         [ui/ModalContent (cond-> {}
                                  scrolling? (assoc :scrolling true)) body]
         [ui/ModalActions
          [action-buttons
           label
           "cancel"
           action-fn
           cancel-fn]]]))))


(defn action-button
  [label title-text body on-confirm on-cancel & [scrolling?]]
  [action-button-icon label nil title-text body on-confirm on-cancel scrolling?])


(defn edit-button
  "Creates an edit that will bring up an edit dialog and will save the
   modified resource when saved."
  [{:keys [id] :as data} description action-fn]
  (let [tr (subscribe [::i18n-subs/tr])
        text (atom (general/edn->json data))
        collection (subscribe [::cimi-subs/collection-name])
        resource-metadata (subscribe [::docs-subs/document @collection])]
    (fn [{:keys [id] :as data} description action-fn]
      [action-button-icon
       (@tr [:raw])
       "pencil"
       (str (@tr [:editing]) " " id)
       [forms/resource-editor id text :resource-meta @resource-metadata]
       (fn []
         (try
           (action-fn (general/json->edn @text))
           (catch :default e
             (action-fn e))))
       #(reset! text (general/edn->json data))
       true])))


(defn delete-button
  "Creates a button that will bring up a delete dialog and will execute the
   delete when confirmed."
  [data action-fn]
  (let [tr (subscribe [::i18n-subs/tr])]
    (fn [data action-fn]
      [action-button-icon
       (@tr [:delete])
       "trash"
       (@tr [:delete-resource])
       [:p (@tr [:delete-resource-msg] [(:id data)])]
       action-fn
       (constantly nil)])))


(defn other-button
  "Creates a button that will bring up a dialog to confirm the given action."
  [label data action-fn]
  (let [tr (subscribe [::i18n-subs/tr])]
    (fn [label data action-fn]
      [action-button-icon
       label
       (case label
         "download" "cloud download"
         "upload" "cloud upload"
         "describe" "info"
         "ready" "check"
         "start" "rocket"
         "stop" "stop"
         "cog")
       (@tr [:execute-action] [label])
       [:p (@tr [:execute-action-msg] [label (:id data)])]
       action-fn
       (constantly nil)])))


;; Explicit keys have been added to the operation buttons to avoid react
;; errors for duplicate keys, which may happen when the data contains :key.
;; It is probably a bad idea to have a first argument that can be a map
;; as this will be confused with reagent options.
(defn operation-button [{:keys [id] :as data} description [label href operation-uri]]
  (case label
    "edit" ^{:key "edit"} [edit-button data description #(dispatch [::api-detail-events/edit id %])]
    "delete" ^{:key "delete"} [delete-button data #(dispatch [::api-detail-events/delete id])]
    ^{:key operation-uri} [other-button label data #(dispatch [::api-detail-events/operation id operation-uri])]))


(defn format-operations [refresh-button {:keys [operations] :as data} base-uri description]
  (let [ops (map (juxt #(general/operation-name (:rel %)) #(str base-uri (:href %)) :rel) operations)]
    (vec (concat [refresh-button] (map (partial operation-button data description) ops)))))


(defn metadata-row
  [k v]
  (let [value (cond
                (vector? v) v
                (map? v) (with-out-str (pprint v))
                :else (str v))]
    [ui/TableRow
     [ui/TableCell {:collapsing true} (str k)]
     [ui/TableCell value]]))


(defn detail-header
  [{:keys [id resourceURI created updated
           name description properties acl] :as data}]
  (when data
    [cc/metadata
     {:title       (or name id)
      :subtitle    (when name id)
      :description description
      :icon        "file"
      :updated     updated
      :acl         acl
      :properties  properties}
     (cond-> []
             id (conj (metadata-row "id" id))
             resourceURI (conj (metadata-row "resourceURI" resourceURI))
             name (conj (metadata-row "name" name))
             description (conj (metadata-row "description" description))
             created (conj (metadata-row "created" (time/time-value created)))
             updated (conj (metadata-row "updated" (time/time-value updated))))]))


(defn strip-attr-ns
  "Strips the attribute namespace from the given key."
  [k]
  (last (re-matches #"(?:([^:]*):)?(.*)" (name k))))


(defn tuple-to-row
  [[key value display-name helper]]
  [ui/TableRow
   [ui/TableCell {:collapsing true} (or display-name (str key)) ff/nbsp [ff/help-popup helper]]
   [ui/TableCell {:style {:max-width     "80ex"             ;; FIXME: need to get this from parent container
                          :text-overflow "ellipsis"
                          :overflow      "hidden"}} (if (vector? value)
                                                      (values/format-collection value)
                                                      (values/format-value value))]])


(defn data-to-tuple-fn
  [attributes]
  (fn [[field-name field-value]]
    (let [attributes-map (->> attributes (map (juxt #(-> % :name keyword) identity)) (into {}))]

      [(strip-attr-ns field-name)
       field-value
       (-> attributes-map field-name :displayName)
       (-> attributes-map field-name :description)])))


(defn group-table-sui
  [group-data {:keys [attributes] :as description}]
  (let [data (sort-by first group-data)]
    (table/definition-table (->> data
                                 (map (data-to-tuple-fn attributes))
                                 (map tuple-to-row)))))


(defn detail-menu
  [refresh-button data base-uri description]
  (vec (concat [ui/Menu {:borderless true}]
               (format-operations nil data base-uri description)
               [refresh-button])))


(defn resource-detail
  "Provides a generic visualization of a CIMI resource document."
  [refresh-button data base-uri description]
  (vec (concat
         [ui/Segment style/basic
          (detail-menu refresh-button data base-uri description)
          (detail-header data)
          (group-table-sui (cimi-api-utils/remove-common-attrs data) description)])))
