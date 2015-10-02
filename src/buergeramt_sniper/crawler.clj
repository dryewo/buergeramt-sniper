(ns buergeramt-sniper.crawler
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [print-table]]
            [schema.core :as s]
            [buergeramt-sniper.scraper :as scraper]
            [buergeramt-sniper.loader :as loader]
            [clj-time.format :as tf]
            [clj-time.core :as t])
  (:import (buergeramt_sniper.scraper RootPage CalendarPage DayPage AppointmentPage)
           (org.joda.time DateTime)))

(s/defrecord AvailableDate
  [name :- s/Str
   href :- s/Str
   date :- DateTime])

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

(s/defn get-daytimes-page :- (s/maybe DayPage)
  [href :- s/Str]
  (some-> href
          loader/load-page
          scraper/parse-daytimes-page))

(s/defn get-appointment-page :- (s/maybe AppointmentPage)
  [href :- s/Str]
  (some-> href
          loader/load-page
          scraper/parse-appointment-page))

;(defn extract-prev&next [calendar-page]
;  (->> calendar-page
;       :months
;       (map (juxt :prev-href :next-href))
;       flatten
;       (remove nil?)
;       (into #{})))

(s/defn collect-available-dates :- [AvailableDate]
  [calendar-pages :- [CalendarPage]]
  (for [page calendar-pages
        month (:months page)
        date (:open-dates month)]
    (strict-map->AvailableDate {:name (str (:text date) " " (:name month))
                                :date (:date date)
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

(s/defn between-checker
  "Returns a predicate that checks if the date is between start and end"
  [start :- (s/maybe DateTime)
   end :- (s/maybe DateTime)]
  (log/spy [start end])
  (cond
    (and start end)
    #(t/within? (t/interval start end) (:date %))
    start
    #(t/after? (:date %) start)
    end
    #(t/before? (:date %) end)
    :else
    (constantly true)))

(s/defn gather-data :- [AvailableTime]
  [{{:keys [base-url start-date end-date]} :run-params}]
  (let [root-page (get-root-page base-url)
        calendar-pages (get-all-calendar-pages (:appointment-href root-page))
        available-dates (->> (collect-available-dates calendar-pages)
                             (filter (between-checker start-date end-date)) ;
                             (take 1))                      ; Safety measure to avoid banning
        available-times (get-available-times available-dates)]
    (pretty-print root-page available-times)
    available-times))
