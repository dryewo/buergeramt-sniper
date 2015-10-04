(ns buergeramt-sniper.api
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [print-table]]
            [schema.core :as s]
            [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [buergeramt-sniper.crawler :as crawler]
            [buergeramt-sniper.scheduler :as scheduler]
            [buergeramt-sniper.scraper :as scraper])
  (:gen-class)
  (:import (buergeramt_sniper.crawler BookingResult AvailableTime)
           (buergeramt_sniper.scraper BookingSuccessPage)))

(defn rand-int-between [[low high]]
  (+ low (rand-int (- high low))))

(s/defn pretty-print-available-times
  [available-times :- [AvailableTime]]
  (when (seq available-times)
    (log/info
      (with-out-str
        (print-table [:n :date :time :place]
                     (take 3 (map-indexed #(assoc %2 :n (inc %1)) available-times)))))))

(s/defn do-book :- (s/maybe BookingResult)
  [system initial-calendar-href]
  (when-let [available-times (seq (crawler/gather-available-times system initial-calendar-href))]
    (pretty-print-available-times available-times)
    (crawler/strict-map->BookingResult
      {:success      true
       :booking-page (scraper/strict-map->BookingSuccessPage {:transaction-number "123"
                                                              :rejection-code     "456"
                                                              :info-items         {"Hello" "World"}})})
    #_(crawler/book-appointment system (:href (first available-times)))))

(s/defn pretty-print-booking-success
  [{:keys [info-items]} :- BookingSuccessPage]
  (log/info
    (with-out-str
      (println "\n")
      (println "Successully booked:")
      (println "-------------------")
      (doseq [[k v] info-items]
        (println k v)))))

(defn format-stats [{:keys [req-count start-time]}]
  (let [time-total (t/interval start-time (t/now))
        average-interval-sec (-> time-total t/in-seconds double (/ req-count))]
    (format "Requests: %d, Time elapsed: %d min, Average interval: %.1f sec"
            req-count (-> time-total t/in-minutes) average-interval-sec)))

(def TRY_FREQ [25 35])

(defn try-book-until-success
  "Tries to find earliest available time and book it.
  In case of failure schedules a retry after a few seconds."
  ([system initial-calendar-href]
   (try-book-until-success system initial-calendar-href {:req-count  0
                                                         :start-time (t/now)}))
  ([system initial-calendar-href stats]
   (log/debug (format-stats stats))
   (let [result (do-book system initial-calendar-href)]
     (if (:success result)
       (pretty-print-booking-success (:booking-page result))
       (let [next-try-time (-> (rand-int-between TRY_FREQ) t/secs t/from-now)
             error (-> result :booking-page :error)]
         (if error
           (log/info "Failed because of:" error)
           (log/info "No available time slots found."))
         (log/debug "Trying again at" (tf/unparse (tf/formatters :time) next-try-time))
         (let [new-stats (-> stats
                             (update-in [:req-count] inc))]
           (scheduler/set-close-fn (:scheduler system)
                                   (chime-at [next-try-time]
                                             (fn [_]
                                               (try-book-until-success
                                                 system
                                                 initial-calendar-href
                                                 new-stats))))))))))

(defn start-trying
  "Makes preparational requests and then starts scanning available dates every few seconds."
  [system]
  (let [base-url (-> system :run-params :base-url)
        {:keys [initial-calendar-href]} (crawler/get-root-page system base-url)]
    (if initial-calendar-href
      (try-book-until-success system initial-calendar-href)
      (log/error "Unable to find out the calendar page for" base-url))))