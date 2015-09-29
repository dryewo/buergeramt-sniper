(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [print-table]]
            [com.stuartsierra.component :as component]
            [buergeramt-sniper.crawler :as crawler]
            [buergeramt-sniper.scraper :as scraper])
  (:gen-class))

;;;; Conventions
;;;; get-*        href -> map
;;;; extract-*    map -> map

(defn init [base-url]
  (log/info "Starting system...")
  (let [system (component/system-map
                 :crawler (crawler/map->Crawler {:base-url base-url}))
        started-system (component/start system)]
    (log/info "System started.")
    started-system))

(defn init-debug []
  (init "https://service.berlin.de/dienstleistung/121482/")
  ;(init "https://service.berlin.de/dienstleistung/326423/")
  ;(init "https://service.berlin.de/dienstleistung/120686/")
  )

(defn get-calendar-page [href]
  [href]
  (some-> href
          crawler/load-calendar-page
          scraper/parse-calendar-page))

(defn extract-prev&next [calendar-page]
  (->> calendar-page
       :months
       (map (juxt :prev-href :next-href))
       flatten
       (remove nil?)
       (into #{})))

(defn extract-open-dates [calendar-page]
  (for [month (:months calendar-page)
        date (:open-dates month)]
    {:name (str (:text date) " " (:name month))
     :href (:href date)}))

(defn get-daytimes-page
  [href]
  (some-> href
          crawler/load-page
          scraper/parse-daytimes-page))

(defn pretty-print [open-dates-w-times]
  (let [open-dates-w-times-w-dates (for [od open-dates-w-times]
                                     (update-in od
                                                [:times]
                                                (partial map #(assoc % :date (:name od)))))]
    (log/info (with-out-str (print-table [:n :date :time :place]
                                         (->> open-dates-w-times-w-dates
                                              (map :times)
                                              flatten
                                              (take 20)
                                              (map #(assoc %2 :n %1) (iterate inc 1))))))))

(defn collect-open-dates [{:keys [crawler] :as system}]
  (when-let [first-calendar-href (some-> (crawler/load-root crawler)
                                         (scraper/parse-root-page)
                                         :appointment-href)]
    (when-let [first-calendar-page (get-calendar-page first-calendar-href)]
      (let [open-dates (take 2 (extract-open-dates first-calendar-page))
            open-dates-w-times (for [od open-dates]
                                 (let [daytimes-page (get-daytimes-page (:href od))]
                                   (merge od daytimes-page)))]
        (pretty-print open-dates-w-times)))))

(defn run [system]
  (log/info "Running...")
  (log/spy system)
  (collect-open-dates system)
  (log/info "Done"))

(defn -main
  [base-href & args]
  (run (init base-href)))
