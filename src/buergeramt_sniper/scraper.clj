(ns buergeramt-sniper.scraper
  (:require [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]
            [clojure.string :as str]))

(defrecord RootPage [appointment-link])

(defrecord CalendarPage [months])
(defrecord Month [name])

(defn parse-root-page
  "Parse html content and return RootPage map"
  [body]
  (log/info "Parsing root page," (count body) "bytes")
  (when-let [dom (html/html-snippet body)]
    (when-let [app-link (-> dom
                            (html/select [:div.zmstermin-multi :a.btn])
                            first
                            log/spy)]
      (log/spy (->RootPage (get-in app-link [:attrs :href]))))))

(defn- parse-closed-date
  "Parse date cell and return its text"
  [dom]
  (-> dom html/text str/trim)
  #_{:text (-> dom html/text str/trim)})

(defn- parse-open-date
  "Parse date cell and return its text and href"
  [dom]
  {:text (-> dom html/text str/trim)
   :href (-> dom :attrs :href)})

(defn- parse-month
  "Parse month table and return Month map"
  [dom]
  (let [[month-name] (html/select dom [:th.month])
        open-dates (html/select dom [:td.buchbar])
        closed-dates (html/select dom [:td.nichtbuchbar])]
    (map->Month {:name         (some-> month-name html/text str/trim)
                 :closed-dates (seq (map parse-closed-date closed-dates))
                 :open-dates   (seq (map parse-open-date open-dates))})))

(defn parse-calendar-page
  "Parse html content and return CalendarPage map"
  [body]
  (log/info "Parsing calendar page," (count body) "bytes")
  (when-let [dom (html/html-snippet body)]
    (when-let [months (seq (html/select dom [:div.calendar-month-table]))]
      (log/spy (count months))
      (map->CalendarPage {:months (map parse-month months)}))))