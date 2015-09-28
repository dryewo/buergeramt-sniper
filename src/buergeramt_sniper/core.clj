(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clojurewerkz.urly.core :as urly]
            [buergeramt-sniper.crawler :as crawler]
            [buergeramt-sniper.scraper :as scraper])
  (:gen-class)
  (:import (buergeramt_sniper.scraper CalendarPage)))

(defn init [base-href]
  (log/info "Starting system...")
  (let [system (component/system-map
                 :crawler (crawler/map->Crawler {:base-url base-href}))
        started-system (component/start system)]
    (log/info "System started.")
    started-system))

;; https://service.berlin.de/dienstleistung/121482/
;; https://service.berlin.de/dienstleistung/120686/
(defn init-debug []
  (init "https://service.berlin.de/dienstleistung/121482/")
  ;(init "https://service.berlin.de/dienstleistung/120686/")
  )

(defn resolve-month-hrefs [base-href month]
  (let [resolve-fn #(some->> % (urly/resolve base-href))]
    (-> month
        (update-in [:prev-href] resolve-fn)
        (update-in [:next-href] resolve-fn))))

(defn get-calendar-page [href]
  [href]
  (some->> href
           crawler/load-calendar-page
           scraper/parse-calendar-page))

(defn parse-prev&next [^CalendarPage calendar-page]
  (->> calendar-page
       :months
       (map (juxt :prev-href :next-href))
       flatten
       (remove nil?)
       (into #{})))

(defn extract-open-dates [^CalendarPage calendar-page]
  (for [month (:months calendar-page)
        date (:open-dates month)]
    {:name (str (:text date) " " (:name month))
     :href (:href date)}))

(defn collect-open-dates [{:keys [crawler] :as system}]
  (when-let [initial-href (some-> (crawler/load-root crawler)
                                  (scraper/parse-root-page)
                                  :appointment-href)]
    (when-let [initial-page (get-calendar-page initial-href)]
      (let [;;prev&next (log/spy (parse-prev&next initial-page))
            open-dates (log/spy (extract-open-dates initial-page))]))))

(defn run [system]
  (log/info "Running...")
  (log/spy system)
  (collect-open-dates system)
  (log/info "Done"))

(defn -main
  [base-href & args]
  (run (init base-href)))
