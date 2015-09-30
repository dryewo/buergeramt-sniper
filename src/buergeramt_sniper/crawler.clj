(ns buergeramt-sniper.crawler
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [print-table]]
            [schema.core :as s]
            [buergeramt-sniper.scraper :as scraper]
            [buergeramt-sniper.loader :as loader])
  (:import (buergeramt_sniper.scraper RootPage CalendarPage DayPage)))

(s/defrecord AvailableDate
  [name :- s/Str
   href :- s/Str])

(s/defrecord AvailableTime
  [date :- s/Str
   time :- s/Str
   place :- s/Str
   href :- s/Str])

(s/defn get-root-page :- RootPage
  [href :- s/Str]
  (some-> href
          loader/load-page
          scraper/parse-root-page))

(s/defn get-calendar-page :- (s/maybe CalendarPage)
  [href :- s/Str]
  (some-> href
          loader/load-page
          scraper/parse-calendar-page))

(s/defn get-all-calendar-pages :- [CalendarPage]
  [initial-page-href :- s/Str]
  ; TODO Implement crawling all pages
  ; For simplicity's sake return only the first one
  (->> [(get-calendar-page initial-page-href)]
       (remove nil?)))

(s/defn get-daytimes-page :- DayPage
  [href :- s/Str]
  (some-> href
          loader/load-page
          scraper/parse-daytimes-page))

;(defn extract-prev&next [calendar-page]
;  (->> calendar-page
;       :months
;       (map (juxt :prev-href :next-href))
;       flatten
;       (remove nil?)
;       (into #{})))

(s/defn collect-open-dates :- [AvailableDate]
  [calendar-pages :- [CalendarPage]]
  (for [page calendar-pages
        month (:months page)
        date (:open-dates month)]
    (strict-map->AvailableDate {:name (str (:text date) " " (:name month))
                                :href (:href date)})))

(s/defn get-available-times :- [AvailableTime]
  [available-dates :- [AvailableDate]]
  (for [ad available-dates
        t (:times (get-daytimes-page (:href ad)))]
    (strict-map->AvailableTime (merge t {:date (:name ad)}))))

(s/defn pretty-print
  [root-page :- RootPage
   available-times :- [AvailableTime]]
  (log/info
    (with-out-str
      (println "\n")
      (println (:title root-page))
      (if (seq available-times)
        (print-table [:n :date :time :place]
                     (map-indexed #(assoc %2 :n (inc %1)) available-times))
        (println "No times available")))))

(defn gather-data [{:keys [run-params]}]
  (let [root-page (get-root-page (:base-url run-params))
        calendar-pages (get-all-calendar-pages (:appointment-href root-page))
        open-dates (take 2 (collect-open-dates calendar-pages))
        available-times (take 20 (get-available-times open-dates))]
    (pretty-print root-page available-times)))
