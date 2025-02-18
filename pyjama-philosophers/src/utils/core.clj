(ns utils.core
  (:import (clojure.lang Atom)))

(defn deep-deref [x]
  (cond
    (instance? Atom x) (deep-deref @x)
    (map? x) (into {} (map (fn [[k v]] [k (deep-deref v)]) x))
    (coll? x) (into (empty x) (map deep-deref x))
    :else x))