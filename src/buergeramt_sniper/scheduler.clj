(ns buergeramt-sniper.scheduler
  (:require [schema.core :as s]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]))

(defprotocol IScheduler
  (add-close-fn [this close-fn]))

(s/defrecord Scheduler []
  component/Lifecycle
  (start [this]
    (log/debug "Starting scheduler...")
    (assoc this :close-fns (atom [])))
  (stop [this]
    (log/debug "Stopping scheduler...")
    (doseq [close-fn @(:close-fns this)]
      (close-fn))
    (swap! (:close-fns this) (constantly [])))

  IScheduler
  (add-close-fn [this close-fn]
    (log/debug "Adding close-fn")
    (swap! (:close-fns this) conj close-fn)))