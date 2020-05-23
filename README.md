![Java CI](https://github.com/rickycorte/ing-sw-2020-attorre-burduja-cortelazzo/workflows/Java%20CI/badge.svg)
[![codecov](https://codecov.io/gh/rickycorte/ing-sw-2020-attorre-burduja-cortelazzo/branch/master/graph/badge.svg?token=52N59J99Y8)](https://codecov.io/gh/rickycorte/ing-sw-2020-attorre-burduja-cortelazzo)



# ing-sw-2020-attorre-burduja-cortelazzo
Software engineering project AA2019-2020 Politecnico di Milano

## Group AM44

- ### 10618456 Francesco Attorre ([@FrancescoAttorre](https://github.com/FrancescoAttorre)) <br> francesco.attorre@mail.polimi.it

- ### 10604480 Vlad Burduja ([@Burduja](https://github.com/Burduja)) <br> vladislav.burduja@mail.polimi.it

- ### 10530551 Riccardo Erminio Filippo  Cortelazzo ([@rickycorte](https://github.com/rickycorte)) <br> riccardoerminio.cortelazzo@mail.polimi.it


| Functionality | State |
|:-----------------------|:------------------------------------:|
| Basic rules |[![GREEN](https://placehold.it/15/44bb44/44bb44)](#) |
| Complete rules | [![GREEN](https://placehold.it/15/44bb44/44bb44)](#) |
| Socket | [![GREEN](https://placehold.it/15/44bb44/44bb44)](#) |
| GUI | [![YELLOW](https://placehold.it/15/ffdd00/ffdd00)](#) |
| CLI | [![YELLOW](https://placehold.it/15/ffdd00/ffdd00)](#) |
| Multiple games | [![YELLOW](https://placehold.it/15/ffdd00/ffdd00)](#) |
| Persistence | [![RED](https://placehold.it/15/f03c15/f03c15)](#) |
| Advanced Gods | [![RED](https://placehold.it/15/f03c15/f03c15)](#) |
| Undo | [![RED](https://placehold.it/15/f03c15/f03c15)](#) |

<!--
[![RED](https://placehold.it/15/f03c15/f03c15)](#)
[![YELLOW](https://placehold.it/15/ffdd00/ffdd00)](#)
[![GREEN](https://placehold.it/15/44bb44/44bb44)](#)
-->

## Getting started

After cloning this repo you can:
- Build and run tests with `mvn package`
- Build docs with `mvn javadoc:javadoc`

You can also download the latest build with up-to-date docs from branch `release`. That branch is automatically updated by Github Actions and should not be changed manually.

Notice: Codecov badge shows only Controller and Model coverage.
Network and view tests are not required by specification thus the packages are skipped.