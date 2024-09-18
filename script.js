document.addEventListener('DOMContentLoaded', () => {
    const grid = document.getElementById('grid');
    const svg = document.querySelector('.svg-connector');
    const selectedComponentLabel = document.getElementById('selectedComponent');
    const searchBar = document.getElementById('searchBar');
    const searchInput = document.getElementById('searchInput');
    const searchResults = document.getElementById('searchResults');

    const searchTerms = ['apple', 'orange', 'banana'];
    const modalContainer = document.querySelector('.grid-container');

    let currentDraggingBox = null;
    let offsetX, offsetY;
    let gridPosition = { top: 0, left: 0 };
    let selectedBox = null;
    let isSearchBarActive = false;

    function initiateDragging(box) {
        box.addEventListener('mousedown', (e) => {
            if (isSearchBarActive) return; // Prevent dragging when search bar is active
            if (e.target.classList.contains('connector-point')) return; // Prevent dragging when connector point is clicked

            currentDraggingBox = box;
            offsetX = e.clientX - parseInt(box.style.left);
            offsetY = e.clientY - parseInt(box.style.top);
            box.classList.add('grabbing');

            // Select this box
            selectBox(box);

            document.addEventListener('mouseup', () => {
                if (currentDraggingBox) {
                    currentDraggingBox.classList.remove('grabbing');
                    currentDraggingBox = null;
                    updateConnector();
                }
            }, { once: true });
        });

        box.addEventListener('mousemove', (e) => {
            if (currentDraggingBox && !isSearchBarActive) {
                currentDraggingBox.style.left = `${e.clientX - offsetX}px`;
                currentDraggingBox.style.top = `${e.clientY - offsetY}px`;
                updateConnector();
            }
        });

        box.addEventListener('click', (e) => {
            selectBox(box);
        });
    }

    document.querySelectorAll('.modal-box').forEach(box => initiateDragging(box));

    grid.addEventListener('mousedown', (e) => {
        if (!e.target.closest('.modal-box') && !isSearchBarActive) {
            currentDraggingBox = grid;
            offsetX = e.clientX;
            offsetY = e.clientY;
            document.body.style.cursor = 'grabbing';

            // Deselect any selected box
            deselectBox();

            document.addEventListener('mouseup', () => {
                if (currentDraggingBox === grid) {
                    document.body.style.cursor = 'default';
                    currentDraggingBox = null;
                }
            }, { once: true });
        }
    });

    grid.addEventListener('mousemove', (e) => {
        if (currentDraggingBox === grid && !isSearchBarActive) {
            const deltaX = e.clientX - offsetX;
            const deltaY = e.clientY - offsetY;

            gridPosition.left += deltaX;
            gridPosition.top += deltaY;

            document.querySelectorAll('.modal-box').forEach(box => {
                box.style.left = `${parseInt(box.style.left) + deltaX}px`;
                box.style.top = `${parseInt(box.style.top) + deltaY}px`;
            });

            offsetX = e.clientX;
            offsetY = e.clientY;

            updateConnector();
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && isSearchBarActive) {
            closeSearchBar();
            return;
        }

        if (isSearchBarActive) return; // Prevent movement when search bar is active

        let moveStep = 30; // Faster movement
        switch(e.key) {
            case 'ArrowUp':
            case 'w':
                moveGrid(0, -moveStep);
                break;
            case 'ArrowDown':
            case 's':
                moveGrid(0, moveStep);
                break;
            case 'ArrowLeft':
            case 'a':
                moveGrid(-moveStep, 0);
                break;
            case 'ArrowRight':
            case 'd':
                moveGrid(moveStep, 0);
                break;
            case ' ':
                if (searchBar.style.display === 'block') {
                    closeSearchBar();
                } else {
                    openSearchBar();
                }
                e.preventDefault(); // Prevent the default spacebar scroll action
                break;
        }
    });

    searchInput.addEventListener('input', (e) => {
        const value = e.target.value.toLowerCase();
        searchResults.innerHTML = '';

        const filteredTerms = searchTerms.filter(term => term.includes(value));
        filteredTerms.forEach(term => {
            const div = document.createElement('div');
            div.classList.add('search-result-item');
            div.textContent = term;
            div.addEventListener('click', () => {
                createModalBox(term);
                closeSearchBar();
            });
            searchResults.appendChild(div);
        });

        searchResults.classList.toggle('hidden', filteredTerms.length === 0);
    });

    searchInput.addEventListener('focus', () => {
        searchResults.classList.remove('hidden');
    });

    searchInput.addEventListener('blur', () => {
        setTimeout(() => searchResults.classList.add('hidden'), 200);
    });

    function selectBox(box) {
        // Deselect the previous selected box
        if (selectedBox) {
            selectedBox.classList.remove('selected');
        }
        // Mark the new selected box
        box.classList.add('selected');
        selectedBox = box;

        // Update the sidebar label
        selectedComponentLabel.textContent = box.querySelector('h3').textContent;
    }

    function deselectBox() {
        if (selectedBox) {
            selectedBox.classList.remove('selected');
            selectedBox = null;
            selectedComponentLabel.textContent = 'None';
        }
    }

    function updateConnector() {
        const box1 = document.getElementById('box1');
        const box2 = document.getElementById('box2');
        const connector1 = document.getElementById('connector1');
        const connector2 = document.getElementById('connector2');
        drawConnection(connector1, connector2);
    }

    function drawConnection(startConnector, endConnector) {
        const startRect = startConnector.getBoundingClientRect();
        const endRect = endConnector.getBoundingClientRect();

        const startX = startRect.left + window.scrollX + startRect.width / 2;
        const startY = startRect.top + window.scrollY + startRect.height / 2;
        const endX = endRect.left + window.scrollX + endRect.width / 2;
        const endY = endRect.top + window.scrollY + endRect.height / 2;

        const path = `M ${startX},${startY} C ${startX + 100},${startY} ${endX - 100},${endY} ${endX},${endY}`;
        
        svg.innerHTML = `<path d="${path}" stroke="blue" fill="transparent" stroke-width="2"/>`;
    }

    function moveGrid(deltaX, deltaY) {
        gridPosition.left += deltaX;
        gridPosition.top += deltaY;

        document.querySelectorAll('.modal-box').forEach(box => {
            box.style.left = `${parseInt(box.style.left) + deltaX}px`;
            box.style.top = `${parseInt(box.style.top) + deltaY}px`;
        });

        updateConnector();
    }

    function openSearchBar() {
        isSearchBarActive = true;
        searchBar.style.display = 'block';
        searchInput.focus();
    }

    function closeSearchBar() {
        isSearchBarActive = false;
        searchBar.style.display = 'none';
        searchInput.value = '';
        searchResults.innerHTML = '';
        searchResults.classList.add('hidden');
    }

    function createModalBox(name) {
        const modalBox = document.createElement('div');
        modalBox.classList.add('modal-box');
        modalBox.style.top = '200px';
        modalBox.style.left = '200px';
        modalBox.innerHTML = `<h3 class="text-lg font-bold">${name}</h3><p class="text-sm text-gray-600">Some content here</p><div class="connector-point" style="bottom: -10px; right: -10px;"></div>`;
        modalContainer.appendChild(modalBox);
        
        // Add similar event listeners to the new modal box
        initiateDragging(modalBox);
    }

    document.addEventListener('click', (e) => {
        if (isSearchBarActive && !searchBar.contains(e.target) && !e.target.closest('.modal-box')) {
            closeSearchBar();
        }
    });

    updateConnector();
});
