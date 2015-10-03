(ns buergeramt-sniper.crawler
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [print-table]]
            [schema.core :as s]
            [buergeramt-sniper.scraper :as scraper]
            [buergeramt-sniper.loader :as loader]
            [clj-time.format :as tf]
            [clj-time.core :as t])
  (:import (buergeramt_sniper.scraper RootPage CalendarPage DayPage AppointmentPage)
           (org.joda.time DateTime)
           (buergeramt_sniper.loader Loader)))

(s/defrecord AvailableDate
  [name :- s/Str
   href :- s/Str
   date :- DateTime])

(s/defrecord AvailableTime
  [date :- s/Str
   time :- s/Str
   place :- s/Str
   href :- s/Str])

(defn get-page [system href parser-fn]
  (some->> href
           (loader/load-page (:loader system))
           parser-fn))

(s/defn get-all-calendar-pages :- [CalendarPage]
  [system
   initial-page-href :- s/Str]
  ; TODO Implement crawling all pages
  ; For simplicity's sake return only the first one
  (->> [(get-page system initial-page-href scraper/parse-calendar-page)]
       (remove nil?)))

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
  [system
   available-dates :- [AvailableDate]]
  (for [ad available-dates
        t (:times (get-page system (:href ad) scraper/parse-daytimes-page))]
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
    (and start end) #(t/within? (t/interval start end) (:date %))
    start #(t/after? (:date %) start)
    end #(t/before? (:date %) end)
    :else (constantly true)))

(s/defn gather-available-times :- [AvailableTime]
  [{{:keys [base-url start-date end-date]} :run-params :as system}]
  (let [root-page (get-page system base-url scraper/parse-root-page)
        calendar-pages (get-all-calendar-pages system (:appointment-href root-page))
        available-dates (->> (collect-available-dates calendar-pages)
                             (filter (between-checker start-date end-date)) ;
                             (take 1))                      ; Safety measure to avoid banning
        available-times (get-available-times system available-dates)]
    (pretty-print root-page available-times)
    available-times))

(s/defn book-appointment
  [system href]
  (let [user-data (log/spy (select-keys (:run-params system) [:name :email]))
        {:keys [hidden-inputs]} (s/validate AppointmentPage (log/spy (get-page system href scraper/parse-appointment-page)))]
    (merge hidden-inputs
           user-data)))