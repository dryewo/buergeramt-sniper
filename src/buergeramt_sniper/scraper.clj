(ns buergeramt-sniper.scraper
  (:require [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]
            [clojure.string :as str]))

;;;; Conventions
;;;; parse-*    Enlive DOM -> custom map or record

(defrecord RootPage [appointment-href])

(defrecord CalendarPage [months])
(defrecord Month [name closed-dates open-dates prev-href next-href])

(defn parse-root-page
  "Parse dom tree and return RootPage map"
  [dom]
  (log/info "Parsing root page")
  (when-let [app-link (-> dom
                          (html/select [:div.zmstermin-multi :a.btn])
                          first
                          log/spy)]
    (log/spy (map->RootPage {:appointment-href (-> app-link :attrs :href)}))))

(defn- parse-closed-date
  "Parse date cell and return its text"
  [dom]
  {:text (-> dom html/text str/trim)})

(defn- parse-open-date
  "Parse date cell and return its text and href"
  [dom]
  {:text (-> dom html/text str/trim)
   :href (-> dom
             (html/select [:a])
             first
             :attrs
             :href)})

(defn- parse-month
  "Parse month table and return Month map"
  [dom]
  (let [[month-name] (html/select dom [:th.month])
        [prev-link] (html/select dom [:th.prev :a])
        [next-link] (html/select dom [:th.next :a])
        open-dates (html/select dom [:td.buchbar])
        closed-dates (html/select dom [:td.nichtbuchbar])]
    (map->Month {:name         (some-> month-name html/text str/trim)
                 :closed-dates (map parse-closed-date closed-dates)
                 :open-dates   (map parse-open-date open-dates)
                 :prev-href    (some-> prev-link :attrs :href)
                 :next-href    (some-> next-link :attrs :href)})))

(defn parse-calendar-page
  "Parse html content and return CalendarPage map"
  [dom]
  (log/info "Parsing calendar page")
    (when-let [months (seq (html/select dom [:div.calendar-month-table]))]
      (log/spy (count months))
      (map->CalendarPage {:months (map parse-month months)})))