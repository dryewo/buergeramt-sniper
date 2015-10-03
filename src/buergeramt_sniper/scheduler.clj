(ns buergeramt-sniper.scheduler
  (:require [schema.core :as s]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]))

(defprotocol IScheduler
  (set-close-fn [this close-fn]))

(s/defrecord Scheduler []
  component/Lifecycle
  (start [this]
    (log/debug "Starting scheduler...")
    (assoc this :close-fn (atom nil)))
  (stop [this]
    (log/debug "Stopping scheduler...")
    (swap! (:close-fn this) (fn [old-close-fn]
                              (when old-close-fn
                                (old-close-fn))
                              nil))
    this)

  IScheduler
  (set-close-fn [this close-fn]
    (log/trace "Replacing close-fn")
    (swap! (:close-fn this) (fn [old-close-fn]
                              (when old-close-fn
                                (old-close-fn))
                              close-fn))))