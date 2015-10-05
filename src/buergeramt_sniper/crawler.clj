(ns buergeramt-sniper.crawler
  (:require [clojure.tools.logging :as log]
            [schema.core :as s]
            [buergeramt-sniper.scraper :as scraper]
            [buergeramt-sniper.loader :as loader]
            [clj-time.core :as t])
  (:import (buergeramt_sniper.scraper RootPage CalendarPage AppointmentPage BookingFailurePage BookingSuccessPage)
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

(s/defrecord BookingResult
  [success :- s/Bool
   booking-page :- (s/either BookingSuccessPage BookingFailurePage)])

(defn get-page [system href parser-fn]
  (some-> (loader/load-page-get (:loader system) href)
          parser-fn))

(defn post-page [system href request-opts parser-fn]
  (some-> (loader/load-page-post (:loader system) href request-opts)
          parser-fn))

(s/defn get-root-page :- RootPage
  [system url]
  (get-page system url scraper/parse-root-page))

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

(s/defn between-checker
  "Returns a predicate that checks if the date is between start and end"
  [start_ :- (s/maybe DateTime)
   end_ :- (s/maybe DateTime)]
  ; Adjust start & end for intuitive comparison
  (let [start (some-> start_ (t/minus (t/seconds 1)))
        end (some-> end_ (t/plus (t/days 1)))]
    (cond
      (and start end) #(t/within? (t/interval start end) (:date %))
      start #(t/after? (:date %) start)
      end #(t/before? (:date %) end)
      :else (constantly true))))

(s/defn gather-available-times :- [AvailableTime]
  [system
   intial-calendar-href]
  (let [{:keys [start-date end-date]} (-> system :run-params :options)
        calendar-pages (get-all-calendar-pages system intial-calendar-href)
        available-dates (->> (collect-available-dates calendar-pages)
                             (filter (between-checker start-date end-date)) ;
                             (take 1))                      ; Safety measure to avoid banning
        available-times (get-available-times system available-dates)]
    available-times))

(s/defn book-appointment-fake :- BookingResult
  [_ _]
  (strict-map->BookingResult
    {:success      true
     :booking-page (scraper/strict-map->BookingSuccessPage
                     {:transaction-number "000"
                      :rejection-code     "000"
                      :info-items         {"Dry run completed" ""}})}))

(s/defn book-appointment :- BookingResult
  [system href]
  (let [{:keys [user-form-params]} (-> system :run-params)
        {:keys [hidden-inputs form-action]} (s/validate AppointmentPage (get-page system href scraper/parse-appointment-page))
        booking-response (post-page system
                                    form-action
                                    {:form-params (merge hidden-inputs
                                                         {"agbgelesen" "1"}
                                                         user-form-params)
                                     :headers     {"Referer" href}}
                                    scraper/parse-booking-response-page)]
    (strict-map->BookingResult {:success      (if (:transaction-number booking-response) true false)
                                :booking-page booking-response})))
