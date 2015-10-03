(ns buergeramt-sniper.api
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [print-table]]
            [schema.core :as s]
            [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [buergeramt-sniper.crawler :as crawler]
            [buergeramt-sniper.scheduler :as scheduler])
  (:gen-class)
  (:import (buergeramt_sniper.crawler BookingResult AvailableTime)
           (buergeramt_sniper.scraper BookingSuccessPage)))



(defn rand-int-between [low high]
  (+ low (rand-int (- high low))))

(s/defn pretty-print-available-times
  [available-times :- [AvailableTime]]
  (when (seq available-times)
    (log/info
      (with-out-str
        (print-table [:n :date :time :place]
                     (take 3 (map-indexed #(assoc %2 :n (inc %1)) available-times)))))))

(s/defn do-book [system] :- (s/maybe BookingResult)
  (when-let [available-times (seq (crawler/gather-available-times system))]
    (pretty-print-available-times available-times)
    (crawler/book-appointment system (:href (first available-times)))))

(s/defn pretty-print-booking-success
  [{:keys [info-items]} :- BookingSuccessPage]
  (log/info
    (with-out-str
      (println "\n")
      (println "Successully booked:")
      (println "-------------------")
      (doseq [[k v] info-items]
        (println k v)))))

(defn try-book-until-success
  "Tries to find earliest available time and book it.
  In case of failure schedules a retry after a few seconds."
  [system]
  (let [result (do-book system)]
    (if (:success result)
      (pretty-print-booking-success (:booking-page result))
      (let [next-try-time (-> (rand-int-between 10 20) t/secs t/from-now)
            error (-> result :booking-page :error)]
        (if error
          (log/info "Failed because of:" error)
          (log/info "No available time slots found."))
        (log/debug "Trying again at" (tf/unparse (tf/formatters :time) next-try-time))
        (scheduler/set-close-fn (:scheduler system)
                                (chime-at [next-try-time]
                                          (fn [_] (try-book-until-success system))))))))
