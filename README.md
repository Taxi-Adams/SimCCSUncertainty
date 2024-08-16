# SimCCSUncertainty
Half of/a year's worth of my work as a graduate research assistant at Montana State University from 2022 to 2024. A heat map based max flow min cost solution piece of software for calculating carbon capture and storage pipeline networks robust against uncertainty.

Written in Java version 8, which was the last version to natively support JavaFX. This branch of SimCCS relies on JavaFX features depreciated after Java 8.0.

Gui source package noteworthy classes:
The Gui class controls window display. 
The ControlActions class controls method calls processed from the GUI class and performs back end calculations and data manipulation. 

Datastore source package noteworthy classes:
The DataStorer and DataInOut classes handle data storage and IO.
All other classes act as network objects, such as source, sink, and edges.

Solver source package noteworthy classes:
The MaxFlowRevised and MaxFlowMinCostRevised classes setup mixed integer linear programs for the solution pipeline that are sent to/solved by IBM's CPLEX linear program solver software. These are the final versions of the mixed integer program formulation classes used.
Other solvers are old and for reference (unrevised versions), or are used to solve for delauney triangulations for the candidate pipeline networks on the cost surface used to display networks.

SimCCS 2.0 was originally written by my mentor/research advisor Dr. Sean Yaw, and over the course of my time as a graduate research assistant I created multiple branches of SimCCS for different algorithms we deveoped and implemented into the software. This branch in particular is free for me to share, and contains almost exclusively additions developed by me and planned between us. I implemented back end features for data processing and I/O, wrote multiple mixed integer linear program formulations for use with CPLEX, developed new supporting front end features, with the heat map features in particular being of note. I also contributed to various bug fixes and codebase developments. Finally, I performed feature testing and algorithm branches that were eventually cut such as scaling GUI elements by usage, writing different network solution types (min cost, max flox, and pivoting between solution networks) that are no longer present in the code base. My contributions added somewhere around two to three thousand lines of the final codebase. 
